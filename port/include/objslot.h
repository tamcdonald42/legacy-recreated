/* Septerra Core 1.04 object slot -- derived from BuildObjTables (0x428390)
   and StartSlotEffects (0x4299c0). Offsets verified against the disassembly. */

typedef struct Effect {
    uint8_t pad_000[0x40];
    void * animRoot;   /* resource/animation root */
    unsigned short f044;
    uint8_t pad_046[0x16];
    int state;   /* StartSlotEffects writes 2 */
    int f060;   /* StartSlotEffects writes 2 */
    uint8_t pad_064[0x8];
    int f06c;   /* StartSlotEffects writes 1 */
    uint8_t pad_070[0x120];
    unsigned short frameIndex;   /* index into 0x230-byte frame table */
    uint8_t pad_192[0x2];
    void * animList;   /* StartAnimation appends here */
    uint8_t pad_198[0x4];
    unsigned char loopFlag;   /* StartAnimation picks flag 1 when 0 */
    uint8_t pad_19D[0x7];
    void * f1a4;   /* bytes +0xe/+0xf copied into obj */
    uint8_t pad_1A8[0x20];
} Effect;	/* size 0x1c8 */

typedef struct Obj {
    uint8_t pad_000[0x18];
    unsigned char flags0;   /* byte flags: ActivateObject ors 8 and clears 0x1001; SUB_9 tests bit 1 */
    uint8_t pad_019[0x27];
    void * animRoot;   /* same offset as Effect::animRoot */
    unsigned short f044;
    uint8_t pad_046[0x16];
    int state;   /* == 4 branches in SUB_9 */
    uint8_t pad_060[0xC];
    int f06c;
    uint8_t pad_070[0x32];
    short f0a2;   /* scaled by a 0-100 percentage */
    uint8_t pad_0A4[0x8A];
    int f12e;   /* derived from f0a2, minimum 1 */
    int flags132;   /* ActivateObject ors 0x10000; SUB_9 tests it */
    uint8_t pad_136[0x2E];
    void * f164;   /* copied into slot+4; *(f164+8) into slot+8 */
    uint8_t pad_168[0x28];
    unsigned short frameIndex;   /* same offset as Effect::frameIndex */
    uint8_t pad_192[0x2];
    void * animList;   /* same offset as Effect::animList */
    uint8_t pad_198[0x4];
    unsigned char loopFlag;
    unsigned char f19d;   /* set from *(f1a4+0xf) */
    uint8_t pad_19E[0x6];
    void * f1a4;   /* bytes +0xe/+0xf copied into loopFlag/f19d */
    uint8_t pad_1A8[0x8];
    int f1b0;   /* zeroed by ActivateObject */
    int f1b4;   /* zeroed by ActivateObject */
    int f1b8;   /* zeroed by ActivateObject */
    int f1bc;   /* zeroed by ActivateObject */
    uint8_t pad_1C0[0x8];
} Obj;	/* size 0x1c8 */

typedef struct ObjSlot {
    void * obj;   /* object pointer */
    int animRoot;   /* copy of obj->0x164 */
    int f008;   /* *(obj->0x164 + 8) */
    uint8_t pad_00C[0x21C];
    Effect effects[5];   /* five 0x1C8 effect structs; 0x228 + 5*0x1C8 = 0xB10 */
    int activeEffect;   /* -1 = none; indexes effects[] */
    uint8_t pad_B14[0xB8];
    int fBCC;   /* initialised to 1 */
    int fBD0;   /* initialised to 1 */
    uint8_t pad_BD4[0x14];
} ObjSlot;	/* size 0xbe8 */
