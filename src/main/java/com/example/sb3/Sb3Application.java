package com.example.sb3;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;

@Slf4j
public class Sb3Application {

    public static void main(String[] args) {

        var cs = new DefaultCustomerService();
        var all = cs.all();
        all.forEach(c-> log.info(c.toString()));

    }

}

@Slf4j
    class DefaultCustomerService {

    private final DataSource dataSource;

    DefaultCustomerService() {
        var dataSource = new DriverManagerDataSource(
            "jdbc:postgresql://db.wpjfqirouzfglvfdhfba.supabase.co:5432/postgres",
            "postgres",
            "0n9Evswhohpk4pKe"
        );

        dataSource.setDriverClassName(Driver.class.getName());
        this.dataSource = dataSource;
    }

    Collection<Customer> all(){
        var customers = new ArrayList<Customer>();
        try {
            try (var connection = this.dataSource.getConnection()) {
                try (var stmt = connection.createStatement()) {
                    try (var rs = stmt.executeQuery("SELECT * FROM customers")) {
                        while (rs.next()) {
                            customers.add(new Customer(
                                    rs.getInt("id"),
                                    rs.getString("name")
                            ));
                        }
                    }
                }
            }
        }catch (Exception e){
            log.error("something wrong", e);
        }
        return customers;
    }
}

record Customer (Integer id, String name) {}