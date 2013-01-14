package mod.xie.xm.file;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;


import mod.xie.xm.Work;
import mod.xie.xm.util.ContentManager;
import mod.xie.xm.util.Xie;
import net.minecraft.client.Minecraft;
import net.minecraft.src.ModLoader;
import net.minecraftforge.client.MinecraftForgeClient;

public class SpritesheetManager {

	private List<String> spritesheets;
	private HashMap<String,String> textureRecord;
	
	private SpritesheetManager() {
		spritesheets = new LinkedList<String>();
		textureRecord = new HashMap<String,String>();
	}
	
	private static SpritesheetManager instance;
	
	public static SpritesheetManager get() {
		if (instance==null) instance = new SpritesheetManager();
		return instance;
	}
	
	@SideOnly(Side.CLIENT)
	public void preloadTextures() {
		for (String s : get().spritesheets) {
			MinecraftForgeClient.preloadTexture(s);
		}
	}

	public String getTextureFilepath(String fullname, String path, String filename, boolean addToTextureRecord) {
		// check texture record to see if this block/item has already been allocated a texture
		String textureFilePath;
		if (textureRecord.containsKey(fullname)) {
			textureFilePath = textureRecord.get(fullname);
		} else {	
			// check for URL
			if (filename.startsWith("http://")) return filename;
			
			// check for absolute path declaration
			if (filename.startsWith("/")) {
				textureFilePath = filename;
			} else {
				// if not assume relative path
				if (filename.startsWith(".\\")) filename = filename.substring(2);
				if (filename.startsWith("./")) filename = filename.substring(2);
				textureFilePath = path+"\\"+filename;
			}
		}

		textureFilePath = textureFilePath.replace("\\", "/");
		if (textureFilePath.startsWith(".")) textureFilePath = textureFilePath.substring(1);
		
		// check that image file path has already been validated
		if (!spritesheets.contains(textureFilePath)) {
			if (Xie.validateImageFile(textureFilePath)) {
				spritesheets.add(textureFilePath);
				XieLog.log().finer("Found texture file "+textureFilePath);
			} else {
				XieLog.log().logIncomplete("Texture file "+textureFilePath+" could not be found.");
				return getDefaultTextureFile();
			}
		}
			
		if (addToTextureRecord) textureRecord.put(fullname,textureFilePath);
		return textureFilePath;
	}
	
	protected String getDefaultTextureFile() {
		return "/terrain.png";
	}

	public void addTexture(String text, String filepath) {
		textureRecord.put(text,filepath);		
	}

	// TODO This is a dirty rotten hack. Refactor.
	private final static String SEP = ".";
	public void handleArmourTextures(String fullname, String path, String armorTexture, String armorTextureBottom) {
		if (armorTexture==null) armorTexture = "/armor/cloth_1.png";
		else if (textureRecord.containsKey(fullname+SEP+"armor1")) {
			armorTexture = textureRecord.get(fullname+SEP+"armor1");
		} else {
			armorTexture = getTextureFilepath(fullname, path, armorTexture, false);
			textureRecord.put(fullname+SEP+"armor1", armorTexture);
		}
		
		if (armorTextureBottom==null) armorTextureBottom=armorTexture;
		else if (textureRecord.containsKey(fullname+SEP+"armor2")) {
			armorTextureBottom = textureRecord.get(fullname+SEP+"armor2");
		} else  {
			armorTextureBottom = getTextureFilepath(fullname, path, armorTextureBottom, false);
			textureRecord.put(fullname+SEP+"armor2", armorTextureBottom);
		}
	}
}
