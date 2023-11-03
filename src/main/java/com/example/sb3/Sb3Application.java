package com.example.sb3;

import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.framework.ProxyFactoryBean;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.sendgrid.SendGridProperties;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.Assert;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;

@Slf4j
public class Sb3Application {



    public static void main(String[] args) {

        var applicationContext = new AnnotationConfigApplicationContext();
        applicationContext.register(DataConfiguration.class);
        applicationContext.refresh();
        var cs = applicationContext.getBean(DefaultCustomerService.class);

        var yamato = cs.add("yamato");
//        var geto = cs.add("geto");
        var all = cs.all();
        Assert.state(all.contains(yamato), () -> "yamato not found");

        all.forEach(c-> log.info(c.toString()));

    }
}

@Configuration
class DataConfiguration {

    private static DefaultCustomerService transactionalCustomerService(
            TransactionTemplate tt,
            DefaultCustomerService delegate){
        // 01.
//        var transactionalCustomerService = Proxy.newProxyInstance(
//                ClassLoader.getSystemClassLoader(),
//                new Class[]{CustomerService.class}, new InvocationHandler() {
//                    @Override
//                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
//                        log.info("invoking method: " + method.getName() + " with args: " + Arrays.toString(args));
//                        return tt.execute(status -> {
//                            try {
//                                return method.invoke(delegate, args);
//                            } catch (IllegalAccessException e) {
//                                throw new RuntimeException(e);
//                            } catch (InvocationTargetException e) {
//                                throw new RuntimeException(e);
//                            }
//                        });
//                    }
//                });
//
//        return (CustomerService) transactionalCustomerService;

        // 02.
        ProxyFactoryBean pfb = new ProxyFactoryBean();
        pfb.setTarget(delegate);
        pfb.setProxyTargetClass(true);
        pfb.addAdvice(new MethodInterceptor() {
            @Override
            public Object invoke(MethodInvocation invocation) throws Throwable {
                var method = invocation.getMethod();
                var args = invocation.getArguments();
                return tt.execute(status -> {
                    try {
                        return method.invoke(delegate, args);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    } catch (InvocationTargetException e) {
                        throw new RuntimeException(e);
                    }});
            }
        });

        return (DefaultCustomerService) pfb.getObject();

    }

    @Bean
    DefaultCustomerService defaultCustomerService(TransactionTemplate tt, JdbcTemplate jdbcTemplate){
        return transactionalCustomerService(tt, new DefaultCustomerService(jdbcTemplate));

    }

    @Bean
    TransactionTemplate transactionTemplate(PlatformTransactionManager ptm){
        var tt = new TransactionTemplate(ptm);
        tt.afterPropertiesSet();
        return tt;
    }

    @Bean
    DataSourceTransactionManager platformTransactionManager(DataSource dataSource){
        var ptm = new DataSourceTransactionManager(dataSource);
        ptm.afterPropertiesSet();
        return ptm;
    }

    @Bean
    JdbcTemplate template(DataSource dataSource){
        var template = new JdbcTemplate(dataSource);
        template.afterPropertiesSet();
        return template;
    }


    @Bean
    DriverManagerDataSource dataSource(){
        var dataSource = new DriverManagerDataSource(
                "jdbc:postgresql://db.wpjfqirouzfglvfdhfba.supabase.co:5432/postgres",
                "postgres",
                "2CSjLBnkNI65ebJW"
        );

        dataSource.setDriverClassName(Driver.class.getName());
        return dataSource;
    }

}


//class TransactionalCustomerService extends CustomerService {
//
//    private final TransactionTemplate tt;
//
//    TransactionalCustomerService(JdbcTemplate template, TransactionTemplate tt) {
//        super(template);
//        this.tt = tt;
//    }
//
//    @Override
//    Customer add(String name) {
//        return this.tt.execute(status -> super.add(name));
//    }
//
//    @Override
//    Customer byId(Integer id) {
//        return this.tt.execute(status -> super.byId(id));
//    }
//
//    @Override
//    Collection<Customer> all() {
//        return this.tt.execute(status -> super.all());
//    }
//}

//interface CustomerService{
//    Customer add(String name);
//    Customer byId(Integer id);
//    Collection<Customer> all();
//
//}

@Slf4j
class DefaultCustomerService {
    private final JdbcTemplate template;

    private final RowMapper<Customer> rowMapper = (rs, rowNum)
            -> new Customer(rs.getInt("id"), rs.getString("name"));

    DefaultCustomerService(JdbcTemplate template) {
        this.template = template;
    }

    public Customer add(String name){
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

    public Customer byId(Integer id){
        return template.queryForObject(
                "select id, name from customers where id = ?", rowMapper, id);
    }

    public Collection<Customer> all(){
        return this.template.query("select * from customers", this.rowMapper);
    }
}



record Customer (Integer id, String name) {}