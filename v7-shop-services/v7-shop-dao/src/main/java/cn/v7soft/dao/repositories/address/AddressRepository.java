package cn.v7soft.dao.repositories.address;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

import cn.v7soft.core.repository.BaseRepository;
import cn.v7soft.dao.entities.address.Address;

public interface AddressRepository extends BaseRepository<Address> {
    /**
     * 加载某个国家下的所有省份
     *
     * @return 所有省份
     */
    @Query("select distinct province from Address where status='VALID' order by province")
    List<String> loadProvinces();


    @Query("select distinct city from Address where (:province is NULL or province = :province) and status='VALID'order by city")
    List<String> loadCities( @Param("province") String province);

    @Query("from Address where (:province is NULL or province = :province) and city = :city and status='VALID' order by district")
    List<Address> loadDistricts(@Param("province") String province, @Param("city") String city);

    @Query("from Address where status='VALID'")
    List<Address> loadAll();
}
