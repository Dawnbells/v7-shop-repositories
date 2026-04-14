package cn.v7soft.admin.controller;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONUtil;
import cn.v7soft.admin.controller.req.EditAddressRequest;
import cn.v7soft.admin.controller.req.QueryAddressRequest;
import cn.v7soft.admin.controller.resp.AddressResponse;
import cn.v7soft.admin.service.IAddressService;
import cn.v7soft.admin.service.ITaskExecutorService;
import cn.v7soft.core.controller.BaseController;
import cn.v7soft.dao.entities.address.Address;
import cn.v7soft.dao.entities.primary.AsyncTask;
import cn.v7soft.dao.enums.TaskState;
import cn.v7soft.dao.enums.TaskType;
import cn.v7soft.dao.repositories.primary.AsyncTaskRepository;
import cn.v7soft.dao.repositories.primary.CountryRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Validated
@RestController
@RequestMapping("/address")
@Tag(name = "地址管理")
public class AddressController extends BaseController<Address, IAddressService, AddressResponse, QueryAddressRequest, EditAddressRequest> {

    private final CountryRepository countryRepository;
    private final AsyncTaskRepository asyncTaskRepository;
    private final ITaskExecutorService taskExecutorService;

    protected AddressController(IAddressService service, CountryRepository countryRepository,
                                AsyncTaskRepository asyncTaskRepository, ITaskExecutorService taskExecutorService) {
        super(service);
        this.countryRepository = countryRepository;
        this.asyncTaskRepository = asyncTaskRepository;
        this.taskExecutorService = taskExecutorService;
    }

    @Override
    protected AddressResponse convertEntity(Address address) {
        return AddressResponse.convertEntity(address);
    }

    @Override
    protected Address convertRequest(@Nullable Address dbEntity, EditAddressRequest request) {
        Address address = Optional.ofNullable(dbEntity).orElse(Address.builder().build());
        address.setProvince(request.getProvince());
        address.setCity(request.getCity());
        address.setDistrict(request.getDistrict());
        address.setPostalCode(request.getPostalCode());
        return address;
    }

    @Override
    protected String getPermissionPrefix() {
        return "address";
    }

    @GetMapping("/countries")
    @Operation(summary = "获取已有国家地址表列表")
    public List<String> getCountries() {
        return service.getAddressCountries();
    }

    @PostMapping("/pageByCountry/{countryCode}")
    @Operation(summary = "按国家分页查询地址数据")
    public Map<String, Object> pageByCountry(
            @PathVariable String countryCode,
            @RequestBody Map<String, Object> params) {
        int pageNo = (int) params.getOrDefault("pageNo", 1);
        int pageSize = Math.min((int) params.getOrDefault("pageSize", 20), 100);
        return service.pageByCountry(countryCode, pageNo, pageSize);
    }

    @PostMapping("/remoteAreaPage/{countryCode}")
    @Operation(summary = "按国家分页查询偏远地区数据")
    public Map<String, Object> remoteAreaPage(
            @PathVariable String countryCode,
            @RequestBody Map<String, Object> params) {
        int pageNo = (int) params.getOrDefault("pageNo", 1);
        int pageSize = Math.min((int) params.getOrDefault("pageSize", 20), 100);
        return service.remoteAreaPage(countryCode, pageNo, pageSize);
    }

    @PostMapping("/import")
    @Operation(summary = "导入国家地址库")
    public Map<String, Object> importAddresses(
            @RequestParam("countryCode") String countryCode,
            @RequestParam("file") MultipartFile file) throws IOException {
        countryRepository.getByCode(countryCode.toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("国家代码不存在: " + countryCode));

        String fileName = file.getOriginalFilename();
        String ext = fileName != null && fileName.contains(".") ? fileName.substring(fileName.lastIndexOf(".")) : ".xlsx";
        File tempFile = FileUtil.createTempFile("address", ext, new File("./address-import/"), true);
        file.transferTo(tempFile);

        AsyncTask asyncTask = AsyncTask.builder()
                .taskType(TaskType.ADDRESS_IMPORT)
                .state(TaskState.PENDING)
                .progress(0)
                .name("导入地址库 " + countryCode.toUpperCase())
                .parameters(JSONUtil.toJsonStr(Map.of("countryCode", countryCode.toUpperCase())))
                .uploadFilePath(tempFile.toString())
                .build()
                .fillOwner();
        asyncTask = asyncTaskRepository.saveAndFlush(asyncTask);
        taskExecutorService.submitAsyncTask(asyncTask.getId());

        return Map.of("msg", "导入任务已提交", "taskId", asyncTask.getId());
    }

    @GetMapping("/template")
    @Operation(summary = "下载地址导入模板")
    public ResponseEntity<byte[]> downloadTemplate() {
        byte[] content = service.generateTemplate();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=address_import_template.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(content);
    }
}

