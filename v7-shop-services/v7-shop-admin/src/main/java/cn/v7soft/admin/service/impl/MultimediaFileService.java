package cn.v7soft.admin.service.impl;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.v7soft.admin.controller.resp.MultimediaFileResponse;
import cn.v7soft.admin.service.IMultimediaFileService;
import cn.v7soft.admin.service.IS3Service;
import cn.v7soft.common.service.impl.BaseDataRangeService;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.core.exception.BaseException;
import cn.v7soft.dao.entities.primary.Folder;
import cn.v7soft.dao.entities.primary.MultimediaFile;
import cn.v7soft.dao.enums.MediaState;
import cn.v7soft.dao.enums.MediaType;
import cn.v7soft.dao.properties.MultimediaFileProperty;
import cn.v7soft.dao.repositories.primary.MultimediaFileRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class MultimediaFileService
        extends BaseDataRangeService<MultimediaFile, MultimediaFileRepository>
        implements IMultimediaFileService {

    /**
     * 译图 PUT 的整体时限（含 SDK 内部重试）。必须小于 TurboFlow 插件回传超时（120s），
     * 让服务端先失败并回 503 COMPLETION_RETRY_REQUIRED，而不是插件已放弃、服务端还在后台上传。
     */
    private static final Duration TRANSLATED_IMAGE_UPLOAD_TIMEOUT = Duration.ofSeconds(90);

    private final MultimediaFileProperty multimediaFileProperty;
    private final FolderService folderService;
    private IMultimediaFileService multimediaFileService;
    private final IS3Service s3Service;

    @Lazy
    @Autowired
    public void setMultimediaFileService(IMultimediaFileService multimediaFileService) {
        this.multimediaFileService = multimediaFileService;
    }

    public MultimediaFileService(MultimediaFileRepository repository,
                                 MultimediaFileProperty multimediaFileProperty,
                                 FolderService folderService,
                                 IS3Service s3Service) {
        super(repository);
        this.multimediaFileProperty = multimediaFileProperty;
        this.s3Service = s3Service;
        this.folderService = folderService;
    }

    @Override
    public InputStream download(String id, int width) {
        MultimediaFile multimediaFile = repository.findById(Long.valueOf(id)).orElseThrow(
                () -> ClientResponseEnum.PARAMETER_ILLEGAL.newException("404 Not found"));
//        return aliyunOssService.download(multimediaFile, width);
        return s3Service.download(multimediaFile.getRelativePath());
    }

    @Override
    public List<MultimediaFileResponse> uploadFiles(HttpServletRequest httpServletRequest,
                                                    Long folderId) {
        Folder folder = null;
        if (folderId != null) {
            folder = folderService.getById(folderId);
        }

        List<MultimediaFile> files = new ArrayList<>();

        MultipartHttpServletRequest multiRequest = (MultipartHttpServletRequest) httpServletRequest;
        Iterator<String> fileNames = multiRequest.getFileNames();
        List<MultipartFile> multipartFiles = multiRequest.getFiles(fileNames.next());
        LocalDateTime createTime = LocalDateTime.now();

        for (MultipartFile multipartFile : multipartFiles) {
            long fileSize = multipartFile.getSize();
            long size = (long) Math.ceil(multipartFile.getSize() / (1024 * 1024.0f));

            String name = multipartFile.getOriginalFilename();
            if (StrUtil.isBlank(name)) {
                throw ClientResponseEnum.PARAMETER_ILLEGAL.newException(
                        "很抱歉，未获取到上传的文件信息，请重新上传");
            }
            String suffix = name.substring(name.lastIndexOf(".") + 1).toLowerCase();
            MediaType mediaType;
            if (multimediaFileProperty.getImagesSuffixes().contains(suffix)) {
                mediaType = MediaType.IMAGE;
                if (size > multimediaFileProperty.getMaxImageSize()) {
                    throw ClientResponseEnum.PARAMETER_ILLEGAL.newException(
                            "很抱歉，文件目前只支持上传" + multimediaFileProperty.getMaxImageSize() + "M以内的文件，请压缩后重新上传");
                }
            } else if (multimediaFileProperty.getAudioSuffixes().contains(suffix)) {
                mediaType = MediaType.AUDIO;
                if (size > multimediaFileProperty.getMaxAudioSize()) {
                    throw ClientResponseEnum.PARAMETER_ILLEGAL.newException(
                            "很抱歉，文件目前只支持上传" + multimediaFileProperty.getMaxAudioSize() + "M以内的文件，请压缩后重新上传");
                }
            } else if (multimediaFileProperty.getVideoSuffixes().contains(suffix)) {
                mediaType = MediaType.VIDEO;
                if (size > multimediaFileProperty.getMaxAudioSize()) {
                    throw ClientResponseEnum.PARAMETER_ILLEGAL.newException(
                            "很抱歉，文件目前只支持上传" + multimediaFileProperty.getMaxAudioSize() + "M以内的文件，请压缩后重新上传");
                }
            } else {
                throw ClientResponseEnum.PARAMETER_ILLEGAL.newException(
                        "很抱歉，文件格式非法，请重新上传");
            }

            try (InputStream inputStream = multipartFile.getInputStream()) {
                // 创建PutObjectRequest对象。
                String newFileName = IdUtil.fastSimpleUUID();
                int width = 0;
                int height = 0;
                InputStream uploadStream = inputStream;
                if (mediaType == MediaType.IMAGE) {
                    byte[] imageBytes = inputStream.readAllBytes(); // Java 9+
                    BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(imageBytes));// 检查是否为有效的图片文件
                    width = bufferedImage.getWidth();
                    height = bufferedImage.getHeight();
                    if (width <= 0 || height <= 0) {
                        throw ClientResponseEnum.PARAMETER_ILLEGAL.newException("上传的图片文件无效，请重新上传");
                    }
                    uploadStream = new ByteArrayInputStream(imageBytes);
                }
                String relativePath = MultimediaFileProperty.makeRelativePath(mediaType, newFileName, createTime, suffix);
//                boolean uploaded = aliyunOssService.uploadMultimediaFile(uploadStream, relativePath);
                boolean uploaded = s3Service.upload(uploadStream, relativePath, multipartFile.getContentType());
                if (uploaded) {
                    MultimediaFile multimediaFile = MultimediaFile.builder().name(newFileName)
                            .width(width).height(height)
                            .suffix(suffix).mediaType(mediaType).folder(folder)
                            .relativePath(relativePath).createTime(createTime)
                            .fileSize(fileSize)
                            .mediaState(MediaState.UPLOADED).build();
                    multimediaFile = multimediaFileService.saveAndFlush(multimediaFile);
                    files.add(multimediaFile);
                }
            } catch (Exception e) {
                if (e instanceof BaseException) {
                    throw (BaseException) e;
                }
                e.printStackTrace();
            }
        }
        if (files.isEmpty()) {
            throw ClientResponseEnum.PARAMETER_ILLEGAL.newException("上传失败");
        }
        return files.stream().map(MultimediaFileResponse::convertEntity)
                .collect(Collectors.toList());
    }

    @Override
    public MultimediaFile saveTranslatedImage(byte[] imageBytes, String suffix, cn.v7soft.dao.entities.primary.SystemUser owner) throws Exception {
        String newFileName = IdUtil.fastSimpleUUID();
        LocalDateTime now = LocalDateTime.now();
        String relativePath = MultimediaFileProperty.makeRelativePath(
                MediaType.IMAGE, newFileName, now, suffix);

        String mimeType = "image/" + (suffix.equalsIgnoreCase("jpg") ? "jpeg" : suffix.toLowerCase());
        // 上传失败必须抛出：以前 upload 吞异常返回 false，这里照样落库，产生指向不存在对象的译图记录。
        s3Service.upload(imageBytes, relativePath, mimeType, TRANSLATED_IMAGE_UPLOAD_TIMEOUT);

        int[] size = readImageSize(imageBytes);
        int width = size[0];
        int height = size[1];

        MultimediaFile file = MultimediaFile.builder()
                .name(newFileName).suffix(suffix)
                .width(width).height(height)
                .fileSize(imageBytes.length).mediaType(MediaType.IMAGE)
                .relativePath(relativePath).createTime(now)
                .mediaState(MediaState.UPLOADED).build();
        file.setOwner(owner);
        return multimediaFileService.saveAndFlush(file);
    }

    /**
     * 只读图片头拿宽高，不解码像素。译图多为 2K PNG，ImageIO.read 整图解码要十几 MB 堆和几百毫秒 CPU，
     * 并发回传时会放大 GC 停顿。读不出来返回 0x0，与原先 ImageIO.read 返回 null 时的行为一致。
     */
    static int[] readImageSize(byte[] imageBytes) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(imageBytes))) {
            Iterator<ImageReader> readers = input == null ? null : ImageIO.getImageReaders(input);
            if (readers == null || !readers.hasNext()) {
                return new int[] {0, 0};
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                return new int[] {reader.getWidth(0), reader.getHeight(0)};
            } finally {
                reader.dispose();
            }
        }
    }

    @Override
    public int deleteAllInFolder(Long folderId) {
        return repository.deleteAllInFolder(folderId);
    }

    @Override
    protected void checkKeyConstraint(MultimediaFile entity) {
        // 检查重复逻辑，可根据需要实现
    }
}
