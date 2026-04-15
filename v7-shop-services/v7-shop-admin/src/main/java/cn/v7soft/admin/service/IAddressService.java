package cn.v7soft.admin.service;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import org.springframework.web.multipart.MultipartFile;

import cn.v7soft.core.service.IBaseService;
import cn.v7soft.dao.entities.address.Address;

public interface IAddressService extends IBaseService<Address> {

    List<String> getAddressCountries();

    Map<String, Object> pageByCountry(String countryCode, int pageNo, int pageSize, String keyword);

    Map<String, Object> importAddresses(String countryCode, MultipartFile file);

    /**
     * @param progressCallback (progress 0-100, message) 进度回调
     */
    Map<String, Object> importAddressesFromFile(String countryCode, String filePath,
                                                 BiConsumer<Integer, String> progressCallback);

    Map<String, Object> remoteAreaPage(String countryCode, int pageNo, int pageSize, String keyword);

    byte[] generateTemplate();
}
