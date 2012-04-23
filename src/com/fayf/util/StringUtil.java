package com.fayf.util;

import java.util.LinkedList;
import java.util.List;

public class StringUtil {
	private StringUtil(){}
	
	/**
	 * Finds occurences of a character in a string
	 * 
	 * @param haystack	The string to search in.
	 * @param needle	The character to search for.
	 * @return			A list of indices of where the characters are in <code>haystack</code>
	 */
	public static List<Integer> occurences(String haystack, char needle) {
		List<Integer> idxs = new LinkedList<Integer>();
		int l = haystack.length();
		for (int i = 0; i < l; i++) {
			if (haystack.charAt(i) != needle) continue;
			idxs.add(Integer.valueOf(i));
		}
		return idxs;
	}

	/**
	 * Counts the number of occurences of a character in a string
	 * 
	 * @param haystack	The string to search in.
	 * @param needle	The character to search for.
	 * @return			Number of times <code>needle</code> appears in <code>haystack</code>
	 */
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