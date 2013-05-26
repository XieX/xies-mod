package mod.xie.xm.tex;

import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import mod.xie.xm.Regexie;
import mod.xie.xm.file.XieLog;
import net.minecraft.client.renderer.texture.IconRegister;
import net.minecraft.util.Icon;
import mod.xie.xm.blocks.BlockSide;

public abstract class XieTex {
	public abstract Icon getTexFromSideAndMeta(int i, int j);
	public abstract Icon getIconFromDamage(int par1);
	public abstract XieTexType getType();
	
	protected IconRegister iconRegister;
	
	protected XieTex(IconRegister registrar) {
		iconRegister = registrar;
	}
	
	protected Icon register(String textureName) {
		return iconRegister.registerIcon(textureName);
	}
	
	protected int parseMetavalue(String filename) {
		Matcher matcher = Regexie.X_FILENAME.matcher(filename);
		try {
			return Integer.parseInt(matcher.group(Regexie.GROUP_META));
		} catch (NumberFormatException e) {
			XieLog.log().logFailure("NumberFormatException parsing meta value from filename: "+filename);
			return -1;
		}
	}
	
	protected BlockSide parseSideValue(String filename) {
		Matcher matcher = Regexie.X_FILENAME.matcher(filename);
		
		String sideName = matcher.group(Regexie.GROUP_SIDE);
		return BlockSide.parse(sideName);
	}
	
	// wrapper
	public Icon getIcon(int i, int j) {
		return getTexFromSideAndMeta(i,j);
	}
}
