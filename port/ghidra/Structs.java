import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.data.ArrayDataType;
import ghidra.program.model.data.DataType;
import ghidra.program.model.data.DataTypeComponent;
import ghidra.program.model.data.DataTypeConflictHandler;
import ghidra.program.model.data.DataTypeManager;
import ghidra.program.model.data.IntegerDataType;
import ghidra.program.model.data.PointerDataType;
import ghidra.program.model.data.StructureDataType;
import ghidra.program.model.data.ShortDataType;
import ghidra.program.model.data.Undefined1DataType;
import ghidra.program.model.data.UnsignedShortDataType;
import ghidra.program.model.data.VoidDataType;

import java.io.File;
import java.io.PrintWriter;

/**
 * Define the engine's object slot as real struct types and apply them to .bss.
 *
 * Layout proven from the disassembly of BuildObjTables (0x428390):
 *   mov $0x4bf048,%esi / lea -0x8(%esi),%ebx   -> slot base 0x4bf040
 *   push $0xbe8 / call memset                   -> slot size 0xBE8
 *   mov %eax,(%ebx)                             -> slot[0] = object pointer
 *   movl $0xffffffff,0xb08(%esi)                -> slot+0xB10 = -1
 *   mov %eax,0xbc4(%esi) / 0xbc8(%esi)          -> slot+0xBCC, slot+0xBD0 = 1
 *   add $0xbe8,%esi                             -> stride 0xBE8
 *
 * The five 0x1C8 sub-structs come from StartSlotEffects (0x4299c0), which calls
 * StartAnimation at slot+0x228/0x3f0/0x5b8/0x780/0x948 -- spacing exactly 0x1C8 --
 * and switches on the selector at slot+0xB10.
 *
 * Usage: -postScript Structs.java
 */
public class Structs extends GhidraScript {

    private static final String OUT = "H:/ghidra/work/ghidra-out/";
    private DataTypeManager dtm;

    private void put(StructureDataType st, int off, DataType dt, int len, String name, String cmt) {
        try {
            st.replaceAtOffset(off, dt, len, name, cmt);
        } catch (Exception e) {
            println("  WARN replaceAtOffset(" + Integer.toHexString(off) + ") " + e.getMessage());
        }
    }

    private StructureDataType buildEffect() {
        StructureDataType e = new StructureDataType("Effect", 0x1C8, dtm);
        put(e, 0x040, new PointerDataType(VoidDataType.dataType), 4, "animRoot", "resource/animation root");
        put(e, 0x044, UnsignedShortDataType.dataType, 2, "f044", null);
        put(e, 0x05c, IntegerDataType.dataType, 4, "state", "StartSlotEffects writes 2");
        put(e, 0x060, IntegerDataType.dataType, 4, "f060", "StartSlotEffects writes 2");
        put(e, 0x06c, IntegerDataType.dataType, 4, "f06c", "StartSlotEffects writes 1");
        put(e, 0x190, UnsignedShortDataType.dataType, 2, "frameIndex", "index into 0x230-byte frame table");
        put(e, 0x194, new PointerDataType(VoidDataType.dataType), 4, "animList", "StartAnimation appends here");
        put(e, 0x19c, Undefined1DataType.dataType, 1, "loopFlag", "StartAnimation picks flag 1 when 0");
        put(e, 0x1a4, new PointerDataType(VoidDataType.dataType), 4, "f1a4", "bytes +0xe/+0xf copied into obj");
        return e;
    }

    private StructureDataType buildSlot(DataType effect) {
        StructureDataType s = new StructureDataType("ObjSlot", 0xBE8, dtm);
        put(s, 0x000, new PointerDataType(VoidDataType.dataType), 4, "obj", "object pointer");
        put(s, 0x004, IntegerDataType.dataType, 4, "animRoot", "copy of obj->0x164");
        put(s, 0x008, IntegerDataType.dataType, 4, "f008", "*(obj->0x164 + 8)");
        put(s, 0x228, new ArrayDataType(effect, 5, effect.getLength()), 5 * 0x1C8, "effects",
                "five 0x1C8 effect structs; 0x228 + 5*0x1C8 = 0xB10");
        put(s, 0xB10, IntegerDataType.dataType, 4, "activeEffect", "-1 = none; indexes effects[]");
        put(s, 0xBCC, IntegerDataType.dataType, 4, "fBCC", "initialised to 1");
        put(s, 0xBD0, IntegerDataType.dataType, 4, "fBD0", "initialised to 1");
        return s;
    }

    /** The object itself: same animatable header as Effect, plus the object-only fields. */
    private StructureDataType buildObj() {
        StructureDataType o = new StructureDataType("Obj", 0x1C8, dtm);
        put(o, 0x018, Undefined1DataType.dataType, 1, "flags0",
                "byte flags: ActivateObject ors 8 and clears 0x1001; SUB_9 tests bit 1");
        put(o, 0x040, new PointerDataType(VoidDataType.dataType), 4, "animRoot", "same offset as Effect::animRoot");
        put(o, 0x044, UnsignedShortDataType.dataType, 2, "f044", null);
        put(o, 0x05c, IntegerDataType.dataType, 4, "state", "== 4 branches in SUB_9");
        put(o, 0x06c, IntegerDataType.dataType, 4, "f06c", null);
        put(o, 0x0a2, ShortDataType.dataType, 2, "f0a2", "scaled by a 0-100 percentage");
        put(o, 0x12e, IntegerDataType.dataType, 4, "f12e", "derived from f0a2, minimum 1");
        put(o, 0x132, IntegerDataType.dataType, 4, "flags132", "ActivateObject ors 0x10000; SUB_9 tests it");
        put(o, 0x164, new PointerDataType(VoidDataType.dataType), 4, "f164",
                "copied into slot+4; *(f164+8) into slot+8");
        put(o, 0x190, UnsignedShortDataType.dataType, 2, "frameIndex", "same offset as Effect::frameIndex");
        put(o, 0x194, new PointerDataType(VoidDataType.dataType), 4, "animList", "same offset as Effect::animList");
        put(o, 0x19c, Undefined1DataType.dataType, 1, "loopFlag", null);
        put(o, 0x19d, Undefined1DataType.dataType, 1, "f19d", "set from *(f1a4+0xf)");
        put(o, 0x1a4, new PointerDataType(VoidDataType.dataType), 4, "f1a4", "bytes +0xe/+0xf copied into loopFlag/f19d");
        put(o, 0x1b0, IntegerDataType.dataType, 4, "f1b0", "zeroed by ActivateObject");
        put(o, 0x1b4, IntegerDataType.dataType, 4, "f1b4", "zeroed by ActivateObject");
        put(o, 0x1b8, IntegerDataType.dataType, 4, "f1b8", "zeroed by ActivateObject");
        put(o, 0x1bc, IntegerDataType.dataType, 4, "f1bc", "zeroed by ActivateObject");
        return o;
    }

    private String ctype(DataType dt) {
        String n = dt.getName();
        if (dt instanceof Undefined1DataType) return "unsigned char";
        if (n.equals("undefined") || n.startsWith("undefined")) return null;
        if (dt instanceof PointerDataType) return "void *";
        if (n.equals("int")) return "int";
        if (n.equals("ushort")) return "unsigned short";
        if (n.equals("byte")) return "unsigned char";
        return n;
    }

    private String header(StructureDataType st) {
        StringBuilder sb = new StringBuilder();
        sb.append("typedef struct ").append(st.getName()).append(" {\n");
        int expect = 0;
        for (DataTypeComponent c : st.getComponents()) {
            DataType dt = c.getDataType();
            String ty = ctype(dt);
            if (ty == null) continue;                       // gap: emitted as one pad run
            int off = c.getOffset();
            if (off > expect) {
                sb.append(String.format("    uint8_t pad_%03X[0x%X];%n", expect, off - expect).replace("%n", "\n"));
            }
            String name = c.getFieldName() == null ? "field" : c.getFieldName();
            if (dt instanceof ArrayDataType) {
                ArrayDataType ad = (ArrayDataType) dt;
                String et = ctype(ad.getDataType());
                sb.append(String.format("    %s %s[%d];", et, name, ad.getNumElements()));
            } else {
                sb.append(String.format("    %s %s;", ty, name));
            }
            if (c.getComment() != null) sb.append("   /* ").append(c.getComment()).append(" */");
            sb.append("\n");
            expect = off + c.getLength();
        }
        if (expect < st.getLength()) {
            sb.append(String.format("    uint8_t pad_%03X[0x%X];\n", expect, st.getLength() - expect));
        }
        sb.append("} ").append(st.getName()).append(";\t/* size 0x")
          .append(Integer.toHexString(st.getLength())).append(" */\n");
        return sb.toString();
    }

    private void applyArray(long base, DataType elem, int count, String name) throws Exception {
        Address a = currentProgram.getAddressFactory().getDefaultAddressSpace().getAddress(base);
        clearListing(a, a.add((long) count * elem.getLength() - 1));
        try {
            createData(a, new ArrayDataType(elem, count, elem.getLength()));
            println("APPLIED " + elem.getName() + "[" + count + "] at " + a);
        } catch (Exception e) {
            println("APPLY FAILED at " + a + ": " + e.getMessage());
        }
        ghidra.program.model.symbol.Symbol sym = getSymbolAt(a);
        if (sym != null) {
            sym.setName(name, ghidra.program.model.symbol.SourceType.USER_DEFINED);
            println("renamed " + a + " -> " + name);
        }
    }

    private void renameData(long addr, String name) throws Exception {
        Address a = currentProgram.getAddressFactory().getDefaultAddressSpace().getAddress(addr);
        ghidra.program.model.symbol.Symbol sym = getSymbolAt(a);
        if (sym == null) { println("no symbol at " + a); return; }
        sym.setName(name, ghidra.program.model.symbol.SourceType.USER_DEFINED);
        println("renamed " + a + " -> " + name);
    }

    @Override
    public void run() throws Exception {
        dtm = currentProgram.getDataTypeManager();

        StructureDataType effect = buildEffect();
        StructureDataType slot = buildSlot(effect);
        StructureDataType obj = buildObj();

        DataType eReg = dtm.addDataType(effect, DataTypeConflictHandler.REPLACE_HANDLER);
        DataType sReg = dtm.addDataType(slot, DataTypeConflictHandler.REPLACE_HANDLER);
        DataType oReg = dtm.addDataType(obj, DataTypeConflictHandler.REPLACE_HANDLER);
        println("STRUCT Effect size=0x" + Integer.toHexString(eReg.getLength()));
        println("STRUCT ObjSlot size=0x" + Integer.toHexString(sReg.getLength()));
        println("STRUCT Obj size=0x" + Integer.toHexString(oReg.getLength()));

        applyArray(0x4bf040L, sReg, 13, "g_objSlots");                        // slot array
        applyArray(0x4de628L, new PointerDataType(oReg), 13, "g_levelObjs");  // Obj* array
        renameData(0x4de620L, "g_levelObjCount");

        new File(OUT).mkdirs();
        File h = new File(OUT + "objslot.h");
        try (PrintWriter pw = new PrintWriter(h, "UTF-8")) {
            pw.println("/* Septerra Core 1.04 object slot -- derived from BuildObjTables (0x428390)");
            pw.println("   and StartSlotEffects (0x4299c0). Offsets verified against the disassembly. */");
            pw.println();
            pw.print(header(effect));
            pw.println();
            pw.print(header(obj));
            pw.println();
            pw.print(header(slot));
        }
        println("HEADER written " + h.getAbsolutePath());
    }
}
