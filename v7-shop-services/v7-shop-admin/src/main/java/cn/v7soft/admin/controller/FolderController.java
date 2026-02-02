package cn.v7soft.admin.controller;

import java.util.List;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.v7soft.admin.controller.req.EditFolderRequest;
import cn.v7soft.admin.controller.resp.FolderResponse;
import cn.v7soft.admin.service.IFolderService;
import cn.v7soft.core.controller.request.DeleteRequest;
import cn.v7soft.dao.entities.primary.Folder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/folder")
@Tag(name = "素材中心-文件夹管理")
@Validated
public class FolderController {

    private final IFolderService folderService;

    public FolderController(IFolderService folderService) {
        this.folderService = folderService;
    }

    @PostMapping("/mkdir")
    @Operation(summary = "创建目录")
    public void mkdir(@Valid @RequestBody EditFolderRequest request) {
        folderService.mkdir(request);
    }

    @PostMapping("/rename")
    @Operation(summary = "创建目录")
    public void rename(@Valid @RequestBody EditFolderRequest request) {
        folderService.rename(request);
    }

    @PostMapping("/delete")
    @Operation(summary = "创建目录")
    public void rename(@Valid @RequestBody DeleteRequest request) {
        folderService.delete(request);
    }

    @SaCheckLogin
    @GetMapping("/tree")
    @Operation(summary = "获取目录")
    public List<FolderResponse> getTree(@RequestParam(required = false) String query) {
        List<Folder> topFolders = folderService.treeAllTopFolders(query);
        return topFolders.stream().map(folder -> {
            FolderResponse folderResponse = FolderResponse.filling(folder, FolderResponse.convertEntity(folder));
            folderResponse.setChildren(deepConvertChildren(folder));
            folderResponse.setParentId(folder.getParent() == null ? null : folder.getParent().getId());
            return folderResponse;
        }).toList();
    }

    private List<FolderResponse> deepConvertChildren(Folder folder) {
        if (folder.getChildren() == null || folder.getChildren().isEmpty()) {
            return null;
        }
        return folder.getChildren().stream()
                .map(d -> {
                    FolderResponse folderResponse = FolderResponse.filling(d, FolderResponse.convertEntity(d));
                    folderResponse.setChildren(deepConvertChildren(d));
                    folderResponse.setParentId(d.getParent() != null ? d.getParent().getId() : null);
                    return folderResponse;
                }).toList();
    }
}
