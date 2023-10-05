package com.example.model;
import org.springframework.data.repository.CrudRepository;
// 1й параметр: класс который описывает таблицу, 2й primary key
public interface UserRepository extends CrudRepository <User, Long>{

}
