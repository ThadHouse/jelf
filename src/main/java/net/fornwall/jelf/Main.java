package net.fornwall.jelf;

import static net.fornwall.jelf.ElfSymbol.STT_FUNC;
import static net.fornwall.jelf.ElfSection.SHT_DYNSYM;
import static net.fornwall.jelf.ElfSymbol.BINDING_GLOBAL;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Main {

	public static void main(String[] args) throws Exception {

		// Parse the file.
		ElfFile elfFile = ElfFile.fromFile(new File("libwpiHald.so"));

		List<String> symbols = new ArrayList<>();

		for (int i = 0; i < elfFile.num_sh; i++) {
			ElfSection sh = elfFile.getSection(i);
			int numSymbols = sh.getNumberOfSymbols();
			if (sh.type != SHT_DYNSYM) {
				continue;
			}
			for (int j = 0; j < numSymbols; j++) {
				ElfSymbol sym = sh.getELFSymbol(j);
				if (sym.getType() == STT_FUNC && sym.getBinding() == BINDING_GLOBAL) {
					System.out.println(sym.getName() + " " + sh.getName());
					symbols.add(sym.getName() + " " + sh.getName());
				}

			}
		}

		Files.write(new File("symbols.txt").toPath() , symbols, Charset.defaultCharset());

		return;

		// System.out.println("ELF File: " + "libcameraserverd.so");

		// System.out.println("ELF object size: " + ((elfFile.objectSize == 0) ? "Invalid Object Size" : (elfFile.objectSize == 1) ? "32-bit" : "64-bit"));
		// System.out.println("ELF data encoding: " + ((elfFile.encoding == 0) ? "Invalid Data Encoding" : (elfFile.encoding == 1) ? "LSB" : "MSB"));

		// System.out.println("--> Start: reading " + elfFile.num_sh + " section headers.");
		// for (int i = 0; i < elfFile.num_sh; i++) {
		// 	ElfSection sh = elfFile.getSection(i);
		// 	int numSymbols = sh.getNumberOfSymbols();
		// 	System.out.println("----> Start: Section (" + i + "): " + sh + ", numSymbols=" + numSymbols);

		// 	for (int j = 0; j < numSymbols; j++) {
		// 		ElfSymbol sym = sh.getELFSymbol(j);
		// 		//System.out.println("   " + sym);
		// 	}

		// 	if (sh.type == ElfSection.SHT_STRTAB) {
		// 		System.out.println("------> Start: reading string table.");
		// 		// ElfStringTable st = sh.getStringTable();
		// 		System.out.println("<------ End: reading string table.");
		// 	} else if (sh.type == ElfSection.SHT_HASH) {
		// 		System.out.println("------> Start: reading hash table.");
		// 		sh.getHashTable();
		// 		System.out.println("<------ End: reading hash table.");
		// 	}
		// 	System.out.println("<---- End: Section (" + i + ")");
		// }
		// System.out.println("<-- End: reading " + elfFile.num_sh + " section headers.");

		// System.out.println("--> Start: reading " + elfFile.num_ph + " program headers.");
		// for (int i = 0; i < elfFile.num_ph; i++) {
		// 	ElfSegment ph = elfFile.getProgramHeader(i);
		// 	System.out.println("   " + ph);
		// 	if (ph.type == ElfSegment.PT_INTERP) {
		// 		System.out.println("   INTERPRETER: " + ph.getIntepreter());
		// 	}
		// }
		// System.out.println("<-- End: reading " + elfFile.num_ph + " program headers.");

	}
}
