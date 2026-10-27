package main.java.com.mesac.model;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "records")
public class Record {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID recordId;

    private String recordHolder;

    // Default constructor (Required by JPA)
    public Record() {}

    // Parameterized constructor
    public Record(String recordHolder) {
        this.recordHolder = recordHolder;
    }

    // Getters and Setters
    public UUID getRecordId() {
        return recordId;
    }

    public void setRecordId(UUID recordId) {
        this.recordId = recordId;
    }

    public String getRecordHolder() {
        return recordHolder;
    }

    public void setRecordHolder(String recordHolder) {
        this.recordHolder = recordHolder;
    }
}