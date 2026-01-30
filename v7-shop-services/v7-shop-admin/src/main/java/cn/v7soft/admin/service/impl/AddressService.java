package cn.v7soft.admin.service.impl;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cn.v7soft.admin.service.IAddressService;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.core.enums.StatusEnum;
import cn.v7soft.core.service.impl.BaseService;
import cn.v7soft.dao.entities.address.Address;
import cn.v7soft.dao.repositories.address.AddressRepository;
import jakarta.persistence.Query;

@Service
public class AddressService extends BaseService<Address, AddressRepository> implements IAddressService {

    public AddressService(AddressRepository repository) {
        super(repository);
    }

    @Override
    @Transactional(transactionManager = "addressTransactionManager")
    public Optional<Address> findById(Long id) {
        return this.repository.findById(id);
    }

    @Override
    @Transactional(transactionManager = "addressTransactionManager")
    public Address save(Address data) {
        checkKeyConstraint(data);
        return this.repository.save(data);
    }


    @Override
    @Transactional(transactionManager = "addressTransactionManager")
    public Address saveAndFlush(Address data) {
        checkKeyConstraint(data);
        return this.repository.saveAndFlush(data);
    }


    @Override
    @Transactional(transactionManager = "addressTransactionManager")
    public void delete(Long id) {
        ClientResponseEnum.PARAMETER_ILLEGAL.notNull(id, "ID不能为空");
        Address t = getById(id);
        t.setStatus(StatusEnum.DELETED);
        save(t);
    }

    @Override
    @Transactional(transactionManager = "addressTransactionManager")
    public void switchStatus(Long id, StatusEnum status) {
        ClientResponseEnum.PARAMETER_ILLEGAL.notNull(id, "ID不能为空");
        Address t = getById(id);
        ClientResponseEnum.PARAMETER_ILLEGAL.assertTrue(t.getStatus() != StatusEnum.DELETED, "已删除");
        t.setStatus(status);
        save(t);
    }

    @Override
    @Transactional(transactionManager = "addressTransactionManager")
    public void deleteAll(List<Long> ids) {
        // 构建 SQL 语句，确保 tableName 来自安全源以避免 SQL 注入
        @SuppressWarnings("SqlCurrentSchemaInspection")
        String sql = "UPDATE " + getTableName(type) + " SET `status`='DELETED' WHERE id IN :ids";
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("ids", ids);
        query.executeUpdate();
    }

    @Override
    protected void checkKeyConstraint(Address data) {
        // 可以添加业务逻辑检查，比如地址唯一性验证
    }
}
