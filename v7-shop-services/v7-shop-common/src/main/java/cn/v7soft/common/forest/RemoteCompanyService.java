package cn.v7soft.common.forest;


import cn.v7soft.common.forest.address.RemoteAddress;
import cn.v7soft.common.forest.req.CompanyIdentityRequest;
import cn.v7soft.common.forest.resp.CompanyIdentityResponse;
import cn.v7soft.core.result.CommonResult;
import com.dtflys.forest.annotation.Address;
import com.dtflys.forest.annotation.JSONBody;
import com.dtflys.forest.annotation.Post;
import org.springframework.stereotype.Component;

@Component
@Address(source = RemoteAddress.class)
public interface RemoteCompanyService {
    @Post("/business/company/identity")
    CommonResult<CompanyIdentityResponse> identity(@JSONBody CompanyIdentityRequest request);
}
