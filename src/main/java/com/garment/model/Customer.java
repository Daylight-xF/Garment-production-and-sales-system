package com.garment.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "customers")
public class Customer {

    @Id
    private String id;

    private String name;

    private String phone;

    private String email;

    private String address;

    private String level;

    private String remark;

    private String createBy;

    @CreatedDate
    private Date createTime;

    @LastModifiedDate
    private Date updateTime;
}
