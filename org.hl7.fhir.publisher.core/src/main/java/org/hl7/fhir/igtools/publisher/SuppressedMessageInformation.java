package org.hl7.fhir.igtools.publisher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.hl7.fhir.igtools.publisher.SuppressedMessageInformation.SuppressedMessageListSorter;

public class SuppressedMessageInformation {

  public class SuppressedMessageListSorter implements Comparator<SuppressedMessage> {

    @Override
    public int compare(SuppressedMessage o1, SuppressedMessage o2) {
      return o1.message.compareTo(o2.message);
    }

  }

  public class SuppressedMessage {
    private String message;
    private int useCount;
    
    public SuppressedMessage(String message) {
      super();
      this.message = message;
    }

    public String getMessage() {
      return message;
    }
    
    public void use() {
      useCount++;
    }

    public int getUseCount() {
      return useCount;
    }
  }
  
  public class Category {
    private String name;
    private List<SuppressedMessage> messages = new ArrayList<>();
  }
  
  private List<Category> categories = new ArrayList<>();

  public boolean contains(String message) {
    if (message == null) {
      return false;
    }
    for (Category c : categories) {
      for (SuppressedMessage sm : c.messages) {
        if (sm.message.contains(message)) {
          sm.use();
          return true;
        }
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

  public List<SuppressedMessage> list(String name) {
    List<SuppressedMessage> list = new ArrayList<>();
    for (Category c : categories) {
      if (c.name.equals(name)) {
        list.addAll(c.messages);
      }
    }
    Collections.sort(list, new SuppressedMessageListSorter());
    return list;
  }

  public void add(String msg, String name) {
    if (msg == null || name == null) {
      return;
    }
    for (Category c : categories) {
      if (c.name.equals(name)) {
        c.messages.add(new SuppressedMessage(msg));
        return;        
      }
    }
    Category c = new Category();
    c.name = name;
    c.messages.add(new SuppressedMessage(msg));
    categories.add(c);
  }
}
