package org.hl7.fhir.igtools.publisher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.hl7.fhir.utilities.validation.ValidationMessage;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueSeverity;

public class SuppressedMessageInformation {

  public class SuppressedMessageListSorter implements Comparator<SuppressedMessage> {

    @Override
    public int compare(SuppressedMessage o1, SuppressedMessage o2) {
      return o1.messageRaw.compareTo(o2.messageRaw);
    }

  }

  public class SuppressedMessage {
    private String messageRaw;
    private String messageComp;
    private int compType;
    private int hintUseCount;
    private int warningUseCount;
    
    public SuppressedMessage(String message) {
      super();
      message = message.trim();
      
      // various special cases 
      if (message.contains("Rule ") && message.contains("' Failed (")) {
        message = message.replace("Rule ", "Constraint failed: ").replace("' Failed (", "' (");
      } else if (message.startsWith("Rule ") && message.endsWith("%")) {
        message = message.replace("Rule ", "Constraint failed: ");
      }

      this.messageRaw = message;
      if (messageRaw.startsWith("%")) {
        if (messageRaw.endsWith("%")) {
          compType = 3;
          messageComp = messageRaw.substring(1, messageRaw.length()-1).toLowerCase();
        } else {
          compType = 2;
          messageComp = messageRaw.substring(1, messageRaw.length()).toLowerCase();
        } 
      } else if (messageRaw.endsWith("%")) {
        compType = 1;
        messageComp = messageRaw.substring(0, messageRaw.length()-1).toLowerCase();
      } else {
        compType = 0;
        messageComp = messageRaw.toLowerCase();
      }
      
    }

    public String getMessageRaw() {
      return messageRaw;
    }
    
    public void useHint() {
      hintUseCount++;
    }

    public void useWarning() {
      warningUseCount++;
    }

    public int getUseCountHint() {
      return hintUseCount;
    }
    
    public int getUseCountWarning() {
      return warningUseCount;
    }
    
    public int getUseCount() {
      return hintUseCount + warningUseCount;
    }

    public boolean matches(String msg) {
      return matchesInner(msg) || matchesInner(msg.replace(" (this may not be a problem, but you should check that it's not intended to match a slice)", ""));
    }
    
    private boolean matchesInner(String msg) {
      switch (compType) {
       case 0: return msg.equals(messageComp);
       case 1: return msg.startsWith(messageComp);
       case 2: return msg.endsWith(messageComp);
       case 3: return msg.contains(messageComp);
      }
      return false;
    }
  }
  
  public class Category {
    private String name;
    private List<SuppressedMessage> messages = new ArrayList<>();
  }
  
  private List<Category> categories = new ArrayList<>();

  public boolean contains(String message, ValidationMessage vMsg) {
    if (message == null) {
      return false;
    }
    String msg = message.toLowerCase().trim();
    for (Category c : categories) {
      for (SuppressedMessage sm : c.messages) {
        boolean match = sm.matches(msg);
        if (match) {
          if (!vMsg.isMatched()) {
            if (vMsg.getLevel() == IssueSeverity.WARNING) {
              sm.useWarning();
            } else {
              sm.useHint();
            }
            vMsg.setMatched(true);
            vMsg.setComment(c.name);
          }
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
