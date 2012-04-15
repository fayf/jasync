package com.fayf.util;

import java.util.LinkedList;
import java.util.List;

public class StringUtil {
	public static List<Integer> occurences(String haystack, char needle) {
		List<Integer> idxs = new LinkedList<Integer>();
		int l = haystack.length();
		for (int i = 0; i < l; i++) {
			if (haystack.charAt(i) != needle) continue;
			idxs.add(Integer.valueOf(i));
		}
		return idxs;
	}

	public static int count(String haystack, char needle) {
		int count = 0;
		int l = haystack.length();
		for (int i = 0; i < l; i++) {
			if (haystack.charAt(i) != needle) continue;
			count++;
		}
		return count;
	}
}