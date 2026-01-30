package cn.v7soft.admin.service.impl;

import cn.hutool.core.util.RuntimeUtil;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.core.service.impl.BaseService;
import cn.v7soft.dao.entities.primary.FrontServer;
import cn.v7soft.dao.repositories.primary.FrontServerRepository;
import cn.v7soft.admin.service.IFrontServerService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class FrontServerService extends BaseService<FrontServer, FrontServerRepository> implements IFrontServerService {

    public FrontServerService(FrontServerRepository repository) {
        super(repository);
    }

    @Override
    public FrontServer getFrontServersByName(String name) {
        return repository.findByName(name).orElseThrow(() -> ClientResponseEnum.PARAMETER_ILLEGAL.newException("服务器不存在"));
    }

    @Override
    public List<FrontServer> getServersByActiveResolutionCount(int minActiveResolutionCount) {
        return repository.findServersByActiveResolutionCount(minActiveResolutionCount);
    }

    @Override
    public FrontServer chooseNext() {
        return null;
    }


    @Override
    @Transactional
    public void deleteAll(List<Long> ids) {
        repository.deleteAllByIdInBatch(ids);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }

    @Override
    protected void checkKeyConstraint(FrontServer frontServer) {
        // 可以添加业务逻辑来检查某些字段的唯一性或其他约束
    }

    @Override
    @Transactional
    public void pushAndRefresh(Long id) {
        RuntimeUtil.exec("sh", "/scripts/push.sh");
        FrontServer frontServer = getById(id);
        frontServer.setRequiredUpdate(true);
        save(frontServer);
    }

    @Override
    public List<FrontServer> listFrontServers() {
        return repository.findAll();
    }
}
