package mod.xie.xm.tex;

public enum XieTexType {
	// A fail texture, for whatever reason there are no textures for this object
	FAIL,
	
	// Same texture, regardless of metadata or side (e.g. sand)
	SIMPLE, 
	
	// Different textures for sides, but no variance by metadata [blocks only] (e.g. grass)
	SIDE_VARIABLE, 
	
	// Different textures for metavalues only (e.g. crops)
	META_VARIABLE,
	
	// Different textures for sides and metavalues [blocks only] (e.g. .... no vanilla example :P)
	COMPLEX
}
