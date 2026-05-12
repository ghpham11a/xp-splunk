package com.ghpham11a.springboot.service;

import com.ghpham11a.springboot.dto.CreateItemRequest;
import com.ghpham11a.springboot.dto.UpdateItemRequest;
import com.ghpham11a.springboot.model.Item;
import java.util.List;

public interface ItemService {

    List<Item> findAll();

    Item findById(String id);

    Item create(CreateItemRequest request);

    Item update(String id, UpdateItemRequest request);

    void delete(String id);
}
