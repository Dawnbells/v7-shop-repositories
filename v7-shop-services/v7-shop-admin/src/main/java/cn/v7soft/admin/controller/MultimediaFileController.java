package cn.v7soft.admin.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.codec.Base62;
import cn.v7soft.admin.controller.req.EditMultimediaFileRequest;
import cn.v7soft.admin.controller.req.QueryMultimediaFileRequest;
import cn.v7soft.admin.controller.resp.MultimediaFileResponse;
import cn.v7soft.admin.controller.resp.PreparatoryOrGetResponse;
import cn.v7soft.admin.service.IMultimediaFileService;
import cn.v7soft.common.controller.BaseDataRangeController;
import cn.v7soft.core.controller.request.DeleteRequest;
import cn.v7soft.core.controller.request.QueryPageRequest;
import cn.v7soft.core.controller.request.attributes.EqualsQueryAttribute;
import cn.v7soft.core.controller.request.attributes.NotQueryAttribute;
import cn.v7soft.core.controller.request.attributes.QueryAttribute;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.core.enums.StatusEnum;
import cn.v7soft.dao.entities.primary.MultimediaFile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.servlet.http.HttpServletRequest;
import jdk.jshell.Snippet;

import org.jetbrains.annotations.Nullable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/multimedia-file")
@Tag(name = "素材中心-多媒体文件管理")
@Validated
public class MultimediaFileController extends BaseDataRangeController<MultimediaFile, IMultimediaFileService, MultimediaFileResponse, QueryMultimediaFileRequest, EditMultimediaFileRequest> {

    public MultimediaFileController(IMultimediaFileService service) {
        super(service);
    }

    @PostMapping("/uploadFiles/{id}")
    public List<MultimediaFileResponse> uploadFiles(HttpServletRequest httpServletRequest, @PathVariable(value = "id", required = false) String compactId) {
        Long realId = parseFolderId(compactId);
        return service.uploadFiles(httpServletRequest, realId);
    }


    @GetMapping("/getById/{id}")
    @Operation(summary = "根据ID获取多媒体资源信息")
    public PreparatoryOrGetResponse getById(@PathVariable("id") String idStr) {
        long id = Long.parseLong(idStr);
        return PreparatoryOrGetResponse.convert(service.getById(id));
    }
    @Override
    protected QueryPageRequest<MultimediaFile> convertQueryPageRequest(QueryMultimediaFileRequest request) {
        if ("10000".equalsIgnoreCase(request.getFolderId())) {
            return super.convertQueryPageRequest(request);
        }
        return super.convertQueryPageRequest(request).addConstraint(request.getFolderId() != null, new QueryAttribute() {
            @Override
            public <T> Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder criteriaBuilder) {
                String compactId = request.getFolderId();
                if ("10001".equals(compactId)) {
                    return criteriaBuilder.isNull(root.get("folder").get("id"));
                }
                Long realId = parseFolderId(compactId);
                return criteriaBuilder.equal(root.get("folder").get("id"), realId);
            }
        }).add(NotQueryAttribute.builder().name("status").value(StatusEnum.DELETED).build());
    }

    @Override
    protected MultimediaFileResponse convertEntity(MultimediaFile entity) {
        return MultimediaFileResponse.convertEntity(entity);
    }

    @Override
    protected MultimediaFile convertRequest(@Nullable MultimediaFile dbEntity, EditMultimediaFileRequest request) {
        MultimediaFile entity = Optional.ofNullable(dbEntity).orElse(MultimediaFile.builder().build());
        BeanUtil.copyProperties(request, entity, "id");
        // 处理关联属性
        return entity;
    }


    @Override
    protected String getPermissionPrefix() {
        return "multimedia-file";
    }

    @Override
    protected boolean cleanupBeforeDelete(DeleteRequest request) {
        return true;
    }

    private Long parseFolderId(String compactId) {
        if (compactId == null
                || "root".equalsIgnoreCase(compactId)
                || "10000".equalsIgnoreCase(compactId)
                || "10001".equalsIgnoreCase(compactId)) {
            return null;
        }

        try {
            return Long.parseLong(Base62.decodeStr(compactId));
        } catch (Exception e) {
            throw ClientResponseEnum.PARAMETER_ILLEGAL.newException("文件夹ID非法");
        }
    }
}
