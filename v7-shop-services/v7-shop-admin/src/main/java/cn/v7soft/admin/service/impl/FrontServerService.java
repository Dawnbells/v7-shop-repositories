package cn.v7soft.admin.service.impl;

import cn.hutool.core.util.RuntimeUtil;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.core.service.impl.BaseService;
import cn.v7soft.dao.entities.primary.FrontServer;
import cn.v7soft.dao.repositories.primary.FrontServerRepository;
import cn.v7soft.admin.service.IFrontServerService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
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
    @Transactional
    public void pushAndRefreshAll() {
        Process process = null;
        try {
            // 状态更新和 push 在同一个事务中。先保存状态，避免 push 成功后再保存状态失败，
            // 导致远程配置已经删除而数据库事务却回滚。
            List<FrontServer> frontServers = listFrontServers();
            for (FrontServer frontServer : frontServers) {
                frontServer.setRequiredUpdate(true);
                save(frontServer);
            }

            // 域名删除会同时修改多台前端服务器目录；所有文件准备完成后只提交一次。
            // inheritIO 避免脚本输出过多时阻塞进程，退出码是提交成功的唯一依据。
            process = new ProcessBuilder("sh", "/scripts/push.sh")
                    .inheritIO()
                    .start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException("push.sh 执行失败，退出码: " + exitCode);
            }

        } catch (IOException e) {
            throw new IllegalStateException("启动 push.sh 失败", e);
        } catch (InterruptedException e) {
            if (process != null) {
                process.destroyForcibly();
            }
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待 push.sh 完成时被中断", e);
        }
    }

    @Override
    public List<FrontServer> listFrontServers() {
        return repository.findAll();
    }
}
