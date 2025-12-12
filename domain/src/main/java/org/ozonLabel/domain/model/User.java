package org.ozonLabel.domain.model;

import jakarta.persistence.*;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Entity
@Table(name = "users")
@Data
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class User {
    @Id
    private Long id;
    private String name;
    private String email;
    @Column(name = "company_name")
    private String companyName;
    private String inn;
    private String phone;
    @Column(name = "ozon_client_id")
    private String ozonClientId;
    @Column(name = "ozon_api_key")
    private String ozonApiKey;
    private String subscription;
}
