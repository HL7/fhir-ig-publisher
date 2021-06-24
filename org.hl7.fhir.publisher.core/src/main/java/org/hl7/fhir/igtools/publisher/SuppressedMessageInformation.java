package org.hl7.fhir.igtools.publisher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class SuppressedMessageInformation {

  private class Category {
    private String name;
    private List<String> messages = new ArrayList<>();
  }
  
  private List<Category> categories = new ArrayList<>();
  
  public boolean contains(String message) {
    if (message == null) {
      return false;
    }
    for (Category c : categories) {
      if (c.messages.contains(message)) {
        return true;
      }
    }
    return false;
  }

  public int count() {
    int res = 0;
    for (Category c : categories) {
      res = res + c.messages.size();
    }
    return res;
  }

  public List<String> categories() {
    List<String> list = new ArrayList<>();
    for (Category c : categories) {
      list.add(c.name);
    }
    Collections.sort(list);
    return list;
  }

  public List<String> list(String name) {
    List<String> list = new ArrayList<>();
    for (Category c : categories) {
      if (c.name.equals(name)) {
        list.addAll(c.messages);
      }
    }
    Collections.sort(list);
    return list;
  }

  public void add(String msg, String name) {
    if (msg == null || name == null) {
      return;
    }
    for (Category c : categories) {
      if (c.name.equals(name)) {
        c.messages.add(msg);
        return;        
      }
    }
    Category c = new Category();
    c.name = name;
    c.messages.add(msg);
    categories.add(c);
  }
}
