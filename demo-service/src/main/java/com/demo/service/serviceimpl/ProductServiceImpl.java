package com.demo.service.serviceimpl;

import com.demo.dto.user.ProductDTO;
import com.demo.entity.Product;
import com.demo.entity.ProductViolation;
import com.demo.exception.ProductNotFoundException;
import com.demo.mapper.ProductMapper;
import com.demo.mapper.productViolationMapper;
import com.demo.service.ProductService;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
@Slf4j
public class ProductServiceImpl implements ProductService {

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private productViolationMapper productViolationMapper;

    @Override
    public PageInfo<ProductDTO> getPendingApprovalProducts(int page, int size, String productName, String category, String status) {
        PageHelper.startPage(page, size);
        List<Product> productList = productMapper.getPendingApprovalProducts(productName, category, status);
        List<ProductDTO> productDTOList = productList.stream()
                .map(product -> {
                    ProductDTO productDTO = new ProductDTO();
                    productDTO.setProductId(product.getId());        // 设置商品ID
                    productDTO.setProductName(product.getTitle());  // 设置商品名称
                    productDTO.setCategory(product.getCategory());  // 设置商品类别
                    productDTO.setStatus(product.getStatus());      // 设置商品状态
                    productDTO.setSubmitTime(product.getCreateTime()); // 设置提交时间
                    return productDTO;
                })
                .collect(Collectors.toList());

        return new PageInfo<>(productDTOList);

    }

    @Override
    public void approveProduct(Long productId, boolean isApproved, String reason) {
        // 1. 根据 productId 查询商品
        Product product = productMapper.getProductById(productId);
        // 2. 更新商品的审核状态（通过/不通过）
        product.setStatus(isApproved ? "通过" : "未通过");
        // 3. 保存审核理由（可选）
        product.setReason(reason);
        // 更新数据库
        productMapper.updateProduct(product);

        // 返回处理结果（示例返回成功）
        log.info("商品审核结果：{}", isApproved ? "通过" : "未通过");
        log.info("审核理由：{}", reason);
        log.info("商品信息：{}", product);
        log.info("商品审核完成");


    }

    @Override
    // 查询商品的违规记录
    public List<ProductViolation> getProductViolations(Long productId) {
        return productViolationMapper.findByProductId(productId);
    }

    @Override
    // 添加商品违规记录
    public void addProductViolation(ProductViolation violation) {
        productViolationMapper.insert(violation);
    }

    @Override
    public void updateProductStatus(Long productId, String status) {

        Product product = productMapper.getProductById(productId);
        if (product == null){
            throw new ProductNotFoundException("商品未找到，ID: " + productId);
        }
        product.setStatus(status);
        productMapper.updateProduct(product);
        log.info("商品状态更新成功，商品ID: {}, 新状态: {}", productId, status);
    }


}
