package cn.v7soft.admin.service;

import cn.v7soft.admin.controller.req.EditProductRequest;
import cn.v7soft.admin.controller.req.TranslateProductRequest;
import cn.v7soft.admin.controller.resp.ProductResponse;
import cn.v7soft.common.service.IBaseDataRangeService;
import cn.v7soft.dao.entities.primary.Product;

import java.util.List;

public interface IProductService extends IBaseDataRangeService<Product> {
    Product createOrUpdateProduct(EditProductRequest request);

    List<String> remoteQueryMerchandise(String query);

    ProductResponse translate(TranslateProductRequest request);
}
