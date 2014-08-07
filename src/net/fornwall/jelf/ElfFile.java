package net.fornwall.jelf;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * <pre>
 * http://man7.org/linux/man-pages/man5/elf.5.html
 * http://en.wikipedia.org/wiki/Executable_and_Linkable_Format
 * http://www.ibm.com/developerworks/library/l-dynamic-libraries/
 * http://downloads.openwatcom.org/ftp/devel/docs/elf-64-gen.pdf
 * 
 * Elf64_Addr, Elf64_Off, Elf64_Xword, Elf64_Sxword: 8 bytes
 * Elf64_Word, Elf64_Sword: 4 bytes
 * Elf64_Half: 2 bytes
 * </pre>
 */
public class ElfFile {

	/** Relocatable file type. */
	public static final int FT_REL = 1;
	/** Executable file type. */
	public static final int FT_EXEC = 2;
	/** Shared object file type. */
	public static final int FT_DYN = 3;
	/** Core file file type. */
	public static final int FT_CORE = 4;

	/** 32-bit objects. */
	public static final byte CLASS_32 = 1;
	/** 64-bit objects. */
	public static final byte CLASS_64 = 2;

	/** LSB data encoding. */
	public static final byte DATA_LSB = 1;
	/** MSB data encoding. */
	public static final byte DATA_MSB = 2;

	/** No architecture type. */
	public static final int ARCH_NONE = 0;
	/** AT&T architecture type. */
	public static final int ARCH_ATT = 1;
	/** SPARC architecture type. */
	public static final int ARCH_SPARC = 2;
	/** Intel 386 architecture type. */
	public static final int ARCH_i386 = 3;
	/** Motorolla 68000 architecture type. */
	public static final int ARCH_68k = 4;
	/** Motorolla 88000 architecture type. */
	public static final int ARCH_88k = 5;
	/** Intel 860 architecture type. */
	public static final int ARCH_i860 = 7;
	/** MIPS architecture type. */
	public static final int ARCH_MIPS = 8;
	public static final int ARCH_ARM = 0x28;
	public static final int ARCH_X86_64 = 0x3E;
	public static final int ARCH_AARCH64 = 0xB7;

	private final ElfParser parser;

	/** Byte identifying the size of objects, either {@link #CLASS_32} or {link {@value #CLASS_64} . */
	public final byte objectSize;

	/**
	 * Returns a byte identifying the data encoding of the processor specific data. This byte will be either
	 * DATA_INVALID, DATA_LSB or DATA_MSB.
	 */
	public final byte encoding;

	/** Identifies the object file type. One of the FT_* constants in the class. */
	public final short file_type; // Elf32_Half
	/** The required architecture. One of the ARCH_* constants in the class. */
	public final short arch; // Elf32_Half
	/** Version */
	public final int version; // Elf32_Word
	/**
	 * Virtual address to which the system first transfers control. If there is no entry point for the file the value is
	 * 0.
	 */
	public final long entry_point; // Elf32_Addr
	/** Program header table offset in bytes. If there is no program header table the value is 0. */
	public final long ph_offset; // Elf32_Off
	/** Section header table offset in bytes. If there is no section header table the value is 0. */
	public final long sh_offset; // Elf32_Off
	/** Processor specific flags. */
	public int flags; // Elf32_Word
	/** ELF header size in bytes. */
	public short eh_size; // Elf32_Half
	/** e_phentsize. Size of one entry in the file's program header table in bytes. All entries are the same size. */
	public final short ph_entry_size; // Elf32_Half
	/** e_phnum. Number of {@link ElfProgramHeader} entries in the program header table, 0 if no entries. */
	public final short num_ph; // Elf32_Half
	/** Section header entry size in bytes. */
	public final short sh_entry_size; // Elf32_Half
	/** Number of entries in the section header table, 0 if no entries. */
	public final short num_sh; // Elf32_Half
	/**
	 * Elf{32,64}_Ehdr#e_shstrndx. Index into the section header table associated with the section name string table.
	 * SH_UNDEF if there is no section name string table.
	 */
	private short sh_string_ndx; // Elf32_Half

	/** MemoizedObject array of section headers associated with this ELF file. */
	private MemoizedObject<ElfSectionHeader>[] sectionHeaders;
	/** MemoizedObject array of program headers associated with this ELF file. */
	private MemoizedObject<ElfProgramHeader>[] programHeaders;

	/** Used to cache symbol table lookup. */
	private ElfSectionHeader symbolTableSection;
	/** Used to cache dynamic symbol table lookup. */
	private ElfSectionHeader dynamicSymbolTableSection;

	private ElfSectionHeader dynamicLinkSection;

	/**
	 * Returns the section header at the specified index. The section header at index 0 is defined as being a undefined
	 * section.
	 */
	public ElfSectionHeader getSectionHeader(int index) throws ElfException, IOException {
		return sectionHeaders[index].getValue();
	}

	/** Returns the section header string table associated with this ELF file. */
	public ElfStringTable getSectionHeaderStringTable() throws ElfException, IOException {
		return getSectionHeader(sh_string_ndx).getStringTable();
	}

	/** Returns the string table associated with this ELF file. */
	public ElfStringTable getStringTable() throws ElfException, IOException {
		return findStringTableWithName(ElfSectionHeader.STRING_TABLE_NAME);
	}

	/**
	 * Returns the dynamic symbol table associated with this ELF file, or null if one does not exist.
	 */
	public ElfStringTable getDynamicStringTable() throws ElfException, IOException {
		return findStringTableWithName(ElfSectionHeader.DYNAMIC_STRING_TABLE_NAME);
	}

	private ElfStringTable findStringTableWithName(String tableName) throws ElfException, IOException {
		// Loop through the section header and look for a section
		// header with the name "tableName". We can ignore entry 0
		// since it is defined as being undefined.
		ElfSectionHeader sh = null;
		for (int i = 1; i < num_sh; i++) {
			sh = getSectionHeader(i);
			if (tableName.equals(sh.getName())) return sh.getStringTable();
		}
		return null;
	}

	/** The {@link ElfSectionHeader#SHT_SYMTAB} section (of which there may be only one), if any. */
	public ElfSectionHeader getSymbolTableSection() throws ElfException, IOException {
		if (symbolTableSection != null) return symbolTableSection;
		symbolTableSection = getSymbolTableSection(ElfSectionHeader.SHT_SYMTAB);
		return symbolTableSection;
	}

	/** The {@link ElfSectionHeader#SHT_DYNSYM} section (of which there may be only one), if any. */
	public ElfSectionHeader getDynamicSymbolTableSection() throws ElfException, IOException {
		if (dynamicSymbolTableSection != null) return dynamicSymbolTableSection;
		dynamicSymbolTableSection = getSymbolTableSection(ElfSectionHeader.SHT_DYNSYM);
		return dynamicSymbolTableSection;
	}

	/** The {@link ElfSectionHeader#SHT_DYNAMIC} section (of which there may be only one). Named ".dynamic". */
	public ElfSectionHeader getDynamicLinkSection() throws IOException {
		if (dynamicLinkSection != null) return dynamicLinkSection;
		dynamicLinkSection = getSymbolTableSection(ElfSectionHeader.SHT_DYNAMIC);
		return dynamicLinkSection;
	}

	private ElfSectionHeader getSymbolTableSection(int type) throws ElfException, IOException {
		ElfSectionHeader sh = null;
		for (int i = 1; i < num_sh; i++) {
			sh = getSectionHeader(i);
			if (sh.type == type) {
				dynamicSymbolTableSection = sh;
				return sh;
			}
		}
		return null;
	}

	/** Returns the elf symbol with the specified name or null if one is not found. */
	public ElfSymbol getELFSymbol(String symbolName) throws ElfException, IOException {
		if (symbolName == null) return null;

		// Check dynamic symbol table for symbol name.
		ElfSymbol symbol = null;
		int numSymbols = 0;
		ElfSectionHeader sh = getDynamicSymbolTableSection();
		if (sh != null) {
			numSymbols = sh.getNumberOfSymbols();
			for (int i = 0; i < Math.ceil(numSymbols / 2); i++) {
				if (symbolName.equals((symbol = sh.getELFSymbol(i)).getName())) {
					return symbol;
				} else if (symbolName.equals((symbol = sh.getELFSymbol(numSymbols - 1 - i)).getName())) {
					return symbol;
				}
			}
		}

		// Check symbol table for symbol name.
		sh = getSymbolTableSection();
		if (sh != null) {
			numSymbols = sh.getNumberOfSymbols();
			for (int i = 0; i < Math.ceil(numSymbols / 2); i++) {
				if (symbolName.equals((symbol = sh.getELFSymbol(i)).getName())) {
					return symbol;
				} else if (symbolName.equals((symbol = sh.getELFSymbol(numSymbols - 1 - i)).getName())) {
					return symbol;
				}
			}
		}
		return null;
	}

	/**
	 * Returns the elf symbol with the specified address or null if one is not found. 'address' is relative to base of
	 * shared object for .so's.
	 */
	public ElfSymbol getELFSymbol(long address) throws ElfException, IOException {
		// Check dynamic symbol table for address.
		ElfSymbol symbol = null;
		int numSymbols = 0;
		long value = 0L;

		ElfSectionHeader sh = getDynamicSymbolTableSection();
		if (sh != null) {
			numSymbols = sh.getNumberOfSymbols();
			for (int i = 0; i < numSymbols; i++) {
				symbol = sh.getELFSymbol(i);
				value = symbol.value;
				if (address >= value && address < value + symbol.size) return symbol;
			}
		}

		// Check symbol table for symbol name.
		sh = getSymbolTableSection();
		if (sh != null) {
			numSymbols = sh.getNumberOfSymbols();
			for (int i = 0; i < numSymbols; i++) {
				symbol = sh.getELFSymbol(i);
				value = symbol.value;
				if (address >= value && address < value + symbol.size) return symbol;
			}
		}
		return null;
	}

	public ElfProgramHeader getProgramHeader(int index) throws IOException {
		return programHeaders[index].getValue();
	}

	public static ElfFile fromStream(InputStream in) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		int totalRead = 0;
		byte[] buffer = new byte[8096];
		boolean firstRead = true;
		while (true) {
			int readNow = in.read(buffer, totalRead, buffer.length - totalRead);
			if (readNow == -1) {
				return fromBytes(baos.toByteArray());
			} else {
				if (firstRead) {
					// Abort early.
					if (readNow < 4) {
						throw new ElfException("Bad first read");
					} else {
						if (!(0x7f == buffer[0] && 'E' == buffer[1] && 'L' == buffer[2] && 'F' == buffer[3]))
							throw new ElfException("Bad magic number for file");
					}
					firstRead = false;
				}
				baos.write(buffer, 0, readNow);
			}
		}
	}

	public static ElfFile fromFile(File file) throws ElfException, IOException {
		byte[] buffer = new byte[(int) file.length()];
		try (FileInputStream in = new FileInputStream(file)) {
			int totalRead = 0;
			while (totalRead < buffer.length) {
				int readNow = in.read(buffer, totalRead, buffer.length - totalRead);
				if (readNow == -1) {
					throw new ElfException("Premature end of file");
				} else {
					totalRead += readNow;
				}
			}
		}
		return new ElfFile(new ByteArrayInputStream(buffer));
	}

	public static ElfFile fromBytes(byte[] buffer) throws ElfException, IOException {
		return new ElfFile(new ByteArrayInputStream(buffer));
	}

	public ElfFile(ByteArrayInputStream baos) throws ElfException, IOException {
		byte[] ident = new byte[16];
		parser = new ElfParser(this, baos);

		int bytesRead = parser.read(ident);
		if (bytesRead != ident.length)
			throw new ElfException("Error reading elf header (read " + bytesRead + "bytes - expected to read " + ident.length + "bytes)");

		if (!(0x7f == ident[0] && 'E' == ident[1] && 'L' == ident[2] && 'F' == ident[3])) throw new ElfException("Bad magic number for file");

		objectSize = ident[4];
		if (!(objectSize == CLASS_32 || objectSize == CLASS_64)) throw new ElfException("Invalid object size class: " + objectSize);
		encoding = ident[5];
		if (!(encoding == DATA_LSB || encoding == DATA_MSB)) throw new ElfException("Invalid encoding: " + encoding);
		int elfVersion = ident[6];
		if (elfVersion != 1) throw new ElfException("Invalid elf version: " + elfVersion);
		// ident[7]; // EI_OSABI, target operating system ABI
		// ident[8]; // EI_ABIVERSION, ABI version. Linux kernel (after at least 2.6) has no definition of it.
		// ident[9-15] // EI_PAD, currently unused.

		file_type = parser.readShort();
		arch = parser.readShort();
		version = parser.readInt();
		entry_point = parser.readIntOrLong();
		ph_offset = parser.readIntOrLong();
		sh_offset = parser.readIntOrLong();
		flags = parser.readInt();
		eh_size = parser.readShort();
		ph_entry_size = parser.readShort();
		num_ph = parser.readShort();
		sh_entry_size = parser.readShort();
		num_sh = parser.readShort();
		if (num_sh == 0) {
			throw new ElfException("e_shnum is SHN_UNDEF(0), which is not supported yet"
					+ " (the actual number of section header table entries is contained in the sh_size field of the section header at index 0)");
		}
		sh_string_ndx = parser.readShort();
		if (sh_string_ndx == /* SHN_XINDEX= */0xffff) {
			throw new ElfException("e_shstrndx is SHN_XINDEX(0xffff), which is not supported yet"
					+ " (the actual index of the section name string table section is contained in the sh_link field of the section header at index 0)");
		}

		sectionHeaders = MemoizedObject.uncheckedArray(num_sh);
		for (int i = 0; i < num_sh; i++) {
			final long sectionHeaderOffset = sh_offset + (i * sh_entry_size);
			sectionHeaders[i] = new MemoizedObject<ElfSectionHeader>() {
				@Override
				public ElfSectionHeader computeValue() throws ElfException, IOException {
					return new ElfSectionHeader(parser, sectionHeaderOffset);
				}
			};
		}

		programHeaders = MemoizedObject.uncheckedArray(num_ph);
		for (int i = 0; i < num_ph; i++) {
			final long programHeaderOffset = ph_offset + (i * ph_entry_size);
			programHeaders[i] = new MemoizedObject<ElfProgramHeader>() {
				@Override
				public ElfProgramHeader computeValue() throws IOException {
					return new ElfProgramHeader(parser, programHeaderOffset);
				}
			};
		}
	}

	/** The interpreter specified by the {@link ElfProgramHeader#PT_INTERP} program header, if any. */
	public String getInterpreter() throws IOException {
		for (int i = 0; i < programHeaders.length; i++) {
			ElfProgramHeader ph = programHeaders[i].getValue();
			if (ph.type == ElfProgramHeader.PT_INTERP) return ph.getIntepreter();
		}
		return null;
	}

}
