package com.garment.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@NoArgsConstructor
@Document(collection = "counters")
public class CounterSequence {

    @Id
    private String id;

    private Long seq;

    @LastModifiedDate
    private Date updateTime;
}
