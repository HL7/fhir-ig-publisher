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
	 * The -threads setting: how many threads, beyond its own, the publisher may use for work that it can do
	 * in parallel.
	 *
	 * @param cores the number of cores (Runtime.availableProcessors())
	 * @return the number asked for, but no more than cores - 1; 0 if the parameter isn't there
	 * @throws IllegalArgumentException if the value isn't a whole number of 0 or more
	 */
	public static int getThreads(String[] args, int cores) {
	  if (!hasNamedParam(args, THREADS)) {
		return 0;
	  }
	  String value = getNamedParam(args, THREADS);
	  try {
		int threads = Integer.parseInt(value == null ? "" : value.trim());
		if (threads >= 0) {
		  return Math.min(threads, Math.max(0, cores - 1));
		}
	  } catch (NumberFormatException e) {
		// reported below
	  }
	  throw new IllegalArgumentException(THREADS+" needs a whole number of 0 or more, not '"+value+"'");
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
