package com.example.sb3;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.Assert;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

@Slf4j
public class Sb3Application {

    public static void main(String[] args) {
        var dataSource = new DriverManagerDataSource(
                "jdbc:postgresql://db.wpjfqirouzfglvfdhfba.supabase.co:5432/postgres",
                "postgres",
                "0n9Evswhohpk4pKe"
        );
        dataSource.setDriverClassName(Driver.class.getName());

        var template = new JdbcTemplate(dataSource);
        template.afterPropertiesSet();

        var ptm = new DataSourceTransactionManager(dataSource);

        var tt = new TransactionTemplate(ptm);
        tt.afterPropertiesSet();


        var cs = new TransactionalCustomerService(template, tt);

        var yamato = cs.add("yamato");
//        var geto = cs.add("geto");
        var all = cs.all();
        Assert.state(all.contains(yamato), () -> "yamato not found");

        all.forEach(c-> log.info(c.toString()));

    }
}

class TransactionalCustomerService extends CustomerService {

    private final TransactionTemplate tt;

    TransactionalCustomerService(JdbcTemplate template, TransactionTemplate tt) {
        super(template);
        this.tt = tt;
    }

    @Override
    Customer add(String name) {
        return this.tt.execute(status -> super.add(name));
    }

    @Override
    Customer byId(Integer id) {
        return this.tt.execute(status -> super.byId(id));
    }

    @Override
    Collection<Customer> all() {
        return this.tt.execute(status -> super.all());
    }
}

@Slf4j
class CustomerService {
    private final JdbcTemplate template;

    private final RowMapper<Customer> rowMapper = (rs, rowNum)
            -> new Customer(rs.getInt("id"), rs.getString("name"));

    CustomerService(JdbcTemplate template) {
        this.template = template;

    }

    Customer add(String name){
            var al = new ArrayList<Map<String, Object>>();
            var hm = new HashMap<String, Object>() {};
            hm.put("id", Long.class);
            al.add(hm);
            var keyholder = new GeneratedKeyHolder(al);
            log.info("cek keyholder {}", keyholder.getKeyList());
            this.template.update(con -> {
                        PreparedStatement ps = con
                                .prepareStatement
                                        ("""
                                            insert into customers (name) values(?)
                                            on conflict on constraint customers_name_key do update set name = excluded.name
                                            """, Statement.RETURN_GENERATED_KEYS);
                        ps.setString(1, name);
                        log.info("cek keyholder {}", keyholder.getKeyList());
                        return ps;
                    },
                    keyholder
            );
            var generatedId = Objects.requireNonNull(keyholder.getKeys()).get("id");
            log.info("generated {}", generatedId);
            Assert.state(generatedId instanceof Number, () -> "generatedId is not a number");
            Number number = (Number) generatedId;
            Assert.isTrue(!name.startsWith("geto"), () -> "name should not start with geto");
            return byId(number.intValue());
    }

    Customer byId(Integer id){
        return template.queryForObject(
                "select id, name from customers where id = ?", rowMapper, id);
    }

    Collection<Customer> all(){
        return this.template.query("select * from customers", this.rowMapper);
    }
}



record Customer (Integer id, String name) {}