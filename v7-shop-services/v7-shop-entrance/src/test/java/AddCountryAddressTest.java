import cn.hutool.core.io.FileUtil;
import cn.v7soft.dao.entities.address.Address;
import cn.v7soft.dao.repositories.address.AddressRepository;
import cn.v7soft.entrance.V7ShopEntranceApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;

@SpringBootTest(classes = V7ShopEntranceApplication.class)
public class AddCountryAddressTest {
    @Autowired
    private AddressRepository addressRepository;

    @Test
    public void testAddCountryAddress() {
        String path = "E:\\DWD100\\Address\\葡萄牙.xlsx";
        List<Address> addressList = new ArrayList<>();
        for (String s : FileUtil.readLines(path, "utf-8")) {
//            Log.get().debug("address: " +  s);
//            System.out.println(s);
            String[] addresses = s.split("\t");
            addressList.add(Address.builder()
                    .postalCode(addresses[0])
                    .province(addresses[1])
                    .city(addresses[2])
                    .district(addresses[3])
                    .build());
//            Log.get().debug("address: " + JSONUtil.toJsonStr(addressList.get(addressList.size() - 1)));
//            System.out.println(JSONUtil.toJsonStr(addressList.get(addressList.size() - 1)));
        }

        addressRepository.saveAllAndFlush(addressList);
    }

    public static void main(String[] args) {
        new AddCountryAddressTest().testAddCountryAddress();
    }
}
