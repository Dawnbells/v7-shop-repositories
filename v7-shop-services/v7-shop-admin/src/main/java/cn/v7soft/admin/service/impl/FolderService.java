package cn.v7soft.admin.service.impl;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cn.v7soft.admin.controller.req.EditFolderRequest;
import cn.v7soft.admin.service.IFolderService;
import cn.v7soft.admin.service.IMultimediaFileService;
import cn.v7soft.common.service.impl.BaseDataRangeService;
import cn.v7soft.core.controller.request.DeleteRequest;
import cn.v7soft.core.controller.request.QueryPageRequest;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.dao.entities.primary.Folder;
import cn.v7soft.dao.repositories.primary.FolderRepository;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class FolderService extends BaseDataRangeService<Folder, FolderRepository> implements IFolderService {

    private IMultimediaFileService multimediaFileService;

    public FolderService(FolderRepository repository) {
        super(repository);
    }

    @Lazy
    @Autowired
    public void setMultimediaFileService(IMultimediaFileService multimediaFileService) {
        this.multimediaFileService = multimediaFileService;
    }

    @Override
    public List<Folder> treeAllTopFolders() {
        return findPaginated(QueryPageRequest.<Folder>fromUnLimit().isNull("parent")).stream().toList();
    }

    @Override
    @Transactional
    public void mkdir(EditFolderRequest request) {
        List<Folder> sameLevelFolders;
        Folder parentFolder = null;
        if (request.getId() == null || "10001".equals(request.getId()) || "10000".equals(request.getId())) {
            // 顶层文件夹, 10001-根目录，10000-所有文件
            sameLevelFolders = treeAllTopFolders();
        } else {
            // 子文件夹
            parentFolder = getById(request.getIdLongValue());
            sameLevelFolders = parentFolder.getChildren();
        }
        Optional<Folder> optional = sameLevelFolders.stream().filter(folder -> folder.getName().equalsIgnoreCase(request.getName())).findAny();
        if (optional.isPresent()) {
            ClientResponseEnum.PARAMETER_ILLEGAL.throwException("文件夹名称不允许重复");
        }

        Folder folder = Folder.builder()
                .name(request.getName())
                .isSensitive(Boolean.TRUE.equals(request.getIsSensitive()))
                .parent(parentFolder)
                .build();
        save(folder);
    }

    @Override
    @Transactional
    public void rename(EditFolderRequest request) {
        ClientResponseEnum.PARAMETER_ILLEGAL.notNull(request.getId(), "参数错");
        Folder folder = getById(request.getIdLongValue());
        folder.setName(request.getName());
        folder.setSensitive(Boolean.TRUE.equals(request.getIsSensitive()));
        save(folder);
    }

    @Override
    @Transactional
    public void delete(DeleteRequest request) {
        for (Long id : request.getIdList()) {
            delete(id);
        }
    }

    @Override
    public void delete(Long id) {
        int lines = multimediaFileService.deleteAllInFolder(id);
        log.debug("删除图片数：{}", lines);
        super.delete(id);
    }
}
