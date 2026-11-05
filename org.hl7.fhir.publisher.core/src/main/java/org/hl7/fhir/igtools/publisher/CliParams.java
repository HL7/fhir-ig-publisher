package org.hl7.fhir.igtools.publisher;

import java.util.ArrayList;
import java.util.List;

public class CliParams {

	public static final String DEBUG_LOG = "-debug-log";
	public static final String TRACE_LOG = "-trace-log";

	public static final String SSRF_PROTECTION_ENABLED_PARAM = "-ssrf-protection-enabled";

	public static final String THREADS = "-threads";

	public static String getNamedParam(String[] args, String param) {
	  boolean found = false;
	  for (String a : args) {
		if (found)
		  return a;
		if (a.equals(param)) {
		  found = true;
		}
	  }
	  return null;
	}

	/**
	 * Returns every value supplied after a repeated flag, in command-line order.
	 * Use for flags that may be specified more than once, e.g. {@code -po a.po -po b.po}.
	 * A trailing occurrence of the flag with no following value is ignored.
	 */
	public static List<String> getNamedParams(String[] args, String param) {
	  List<String> values = new ArrayList<>();
	  for (int i = 0; i < args.length - 1; i++) {
		if (args[i].equals(param)) {
		  values.add(args[i + 1]);
		}
	  }
	  return values;
	}

	/**
	 * The -threads setting: the most threads the publisher may use, counting the main thread.
	 *
	 * @return 1 if the parameter isn't there
	 * @throws IllegalArgumentException if the value isn't a whole number of 1 or more
	 */
	public static int getThreads(String[] args) {
	  if (!hasNamedParam(args, THREADS)) {
		return 1;
	  }
	  String value = getNamedParam(args, THREADS);
	  try {
		int threads = Integer.parseInt(value == null ? "" : value.trim());
		if (threads >= 1) {
		  return threads;
		}
	  } catch (NumberFormatException e) {
		// reported below
	  }
	  throw new IllegalArgumentException(THREADS+" needs a whole number of 1 or more, not '"+value+"'");
	}

	public static boolean hasNamedParam(String[] args, String param) {
	  for (String a : args) {
		if (a.equals(param)) {
		  return true;
		}
	  }
	  return false;
	}
}
