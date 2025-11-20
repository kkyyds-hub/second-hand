package com.demo.service.serviceimpl;

import com.demo.dto.user.ProductDTO;
import com.demo.entity.Product;
import com.demo.mapper.ProductMapper;
import com.demo.service.ProductService;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProductServiceImpl implements ProductService {

    @Autowired
    private ProductMapper productMapper;
    @Override
    public PageInfo<ProductDTO> getPendingApprovalProducts(int page, int size, String productName, String category, String status) {
        PageHelper.startPage(page, size);
        List<Product> productList = productMapper.getPendingApprovalProducts(productName, category, status);
        List<ProductDTO> productDTOList = productList.stream()
                .map(product -> new ProductDTO(product.getId(), product.getName(), product.getCategory(), product.getStatus()))
                .collect(Collectors.toList());
        return new PageInfo<>(productDTOList);

    }
}
