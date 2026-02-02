package cn.v7soft.admin.service.impl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import cn.v7soft.admin.controller.req.EditFolderRequest;
import cn.v7soft.admin.service.IFolderService;
import cn.v7soft.admin.service.IMultimediaFileService;
import cn.v7soft.common.service.impl.BaseDataRangeService;
import cn.v7soft.core.controller.request.BasePageRequest;
import cn.v7soft.core.controller.request.DeleteRequest;
import cn.v7soft.core.controller.request.QueryPageRequest;
import cn.v7soft.core.controller.request.attributes.LikeAttribute;
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
    public List<Folder> treeAllTopFolders(String query) {
        QueryPageRequest<Folder> request = QueryPageRequest.<Folder>fromRequest(
                BasePageRequest.builder().pageSize(100).pageNo(1).build()
        ).isNull("parent");
        
        if (StringUtils.hasText(query)) {
            request.add(LikeAttribute.builder().name("name").value("%" + query.trim() + "%").build());
        }
        
        return findPaginated(request).stream().toList();
    }

    @Override
    @Transactional
    public void mkdir(EditFolderRequest request) {
        Folder parentFolder = null;
        boolean nameExists;
        
        if (request.getId() == null || "10001".equals(request.getId()) || "10000".equals(request.getId())) {
            // 顶层文件夹, 10001-根目录，10000-所有文件
            nameExists = repository.existsByNameInTopLevel(request.getName());
        } else {
            // 子文件夹
            parentFolder = getById(request.getIdLongValue());
            nameExists = repository.existsByNameInParent(request.getName(), parentFolder.getId());
        }
        
        if (nameExists) {
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
