package edu.temple.bookshelf;

import java.io.Serializable;

public class BookStatus implements Serializable {
    int status[];
    public BookStatus(){
        status = new int[8];
    }
    public int getStatus(int bookId){
        return status[bookId];
    }

    public void setStatus(int bookId, int val){
        status[bookId]= val;
    }
}
