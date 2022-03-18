package com.sn1pe2win.main;

import java.util.ArrayList;

import discord4j.rest.util.Color;

public class EmbedData {
	
	public class Field {
		public String name = "";
		public String text = "";
		public boolean inline = false;
	}
	
	private String messageText;
	
	private String url;
	private String author;
	private String authorURL = "";
	private String authorIconURL = "";
	private String title;
	private String description;
	private Color color;
	private String thumbnailURL;
	private String imageURL;
	private String footer;
	private String footerURL;
	private ArrayList<Field> fields = new ArrayList<>();
	
	public EmbedData addField(String name, String text, boolean inline) {
		Field f = new Field();
		f.name = name;
		f.text = text;
		f.inline = inline;
		fields.add(f);
		return this;
	}

	public String getUrl() {
		return url;
	}
	
	public EmbedData setPlainTextMessage(String message) {
		messageText = message;
		return this;
	}

	public EmbedData setUrl(String url) {
		this.url = url;
		return this;
	}

	public String getAuthor() {
		return author;
	}

	public EmbedData setAuthorName(String author) {
		this.author = author;
		return this;
	}

	public String getAuthorURL() {
		return authorURL;
	}

	public EmbedData setAuthorURL(String authorURL) {
		this.authorURL = authorURL;
		return this;
	}

	public String getAuthorIconURL() {
		return authorIconURL;
	}

	public EmbedData setAuthorIconURL(String authorIconURL) {
		this.authorIconURL = authorIconURL;
		return this;
	}

	public String getTitle() {
		return title;
	}

	public EmbedData setTitle(String title) {
		this.title = title;
		return this;
	}

	public String getDescription() {
		return description;
	}

	public EmbedData setDescription(String description) {
		this.description = description;
		return this;
	}

	public Color getColor() {
		return color;
	}

	public EmbedData setColor(Color color) {
		this.color = color;
		return this;
	}

	public String getThumbnailURL() {
		return thumbnailURL;
	}

	public EmbedData setThumbnailURL(String thumbnailURL) {
		this.thumbnailURL = thumbnailURL;
		return this;
	}

	public String getImageURL() {
		return imageURL;
	}

	public EmbedData setImageURL(String imageURL) {
		this.imageURL = imageURL;
		return this;
	}

	public String getFooter() {
		return footer;
	}

	public EmbedData setFooterText(String footer) {
		this.footer = footer;
		return this;
	}

	public String getFooterURL() {
		return footerURL;
	}
	
	public String getPlainMessageText() {
		return messageText;
	}

	public EmbedData setFooterURL(String footerURL) {
		this.footerURL = footerURL;
		return this;
	}
	
	public ArrayList<Field> getFields() {
		return fields;
	}
}
