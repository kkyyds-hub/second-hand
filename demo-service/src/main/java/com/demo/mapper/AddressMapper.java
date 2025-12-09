package com.demo.mapper;

import com.demo.entity.Address;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AddressMapper {
    List<Address> findByUserId(Long currentUserId);
}
