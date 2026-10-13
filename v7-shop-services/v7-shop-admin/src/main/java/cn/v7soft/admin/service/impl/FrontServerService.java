package cn.v7soft.admin.service.impl;

import cn.v7soft.admin.utils.NginxPublisher;
import cn.v7soft.admin.utils.NginxWorkspace;
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
        try (var ignored = NginxWorkspace.lock()) {
            FrontServer frontServer = getById(id);
            frontServer.setRequiredUpdate(true);
            save(frontServer);
            NginxPublisher.push();
        }
    }

    @Override
    @Transactional
    public void pushAndRefreshAll() {
        try (var ignored = NginxWorkspace.lock()) {
            // 所有目录准备完成后同步推送；失败则回滚需要更新的数据库标记。
            List<FrontServer> frontServers = listFrontServers();
            for (FrontServer frontServer : frontServers) {
                frontServer.setRequiredUpdate(true);
                save(frontServer);
            }

            NginxPublisher.push();
        }
    }

    @Override
    public List<FrontServer> listFrontServers() {
        return repository.findAll();
    }
}
