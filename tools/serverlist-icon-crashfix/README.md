# Multiplayer screen crash: `bad Base64 input character`

Standalone tooling for a 1.8.8 client crash. It is unrelated to the Forge 1.16.5 mod in `src/`
and is not part of the Gradle build.

## The crash

```
java.lang.IllegalArgumentException: bad Base64 input character at 1: 191 (decimal)
	at io.netty.handler.codec.base64.Base64.decode(Base64.java:247)
	at net.minecraft.client.gui.ServerListEntryNormal.prepareServerIcon(ServerListEntryNormal.java:254)
	at net.minecraft.client.gui.ServerListEntryNormal.drawEntry(ServerListEntryNormal.java:160)
	...
	at net.minecraft.client.gui.GuiMultiplayer.drawScreen(GuiMultiplayer.java:372)
```

One of the servers in the server list has a favicon that is not valid Base64. `prepareServerIcon`
is where 1.8.8 turns that string into a texture:

```java
ByteBuf bytebuf  = Unpooled.copiedBuffer(this.server.getBase64EncodedIconData(), Charsets.UTF_8);
ByteBuf bytebuf1 = Base64.decode(bytebuf);   // line 254 - outside the try block
try {
    BufferedImage bufferedimage = TextureUtil.readBufferedImage(new ByteBufInputStream(bytebuf1));
    ...
} catch (Throwable throwable) {              // only covers the PNG decode
    logger.error("Invalid icon for server ...", throwable);
    this.server.setBase64EncodedIconData(null);
}
```

The exception table of the shipped class confirms it - the guarded range starts *after* the
`Base64.decode` call:

```
Exception table:
   from    to  target type
     51   111   124   Class java/lang/Throwable      // 46 is the Base64.decode call
```

So a malformed icon is not "invalid icon" logged and dropped; it escapes through `drawEntry` and
kills the render loop. This is vanilla 1.8.8 behaviour, not something the client added.

`191` is the second UTF-8 byte of the string. Netty masks each byte with `0x7F` before the alphabet
lookup, so the leading `0xEF` slips through as `'o'` and the following `0xBF` is what it reports.
`EF BF BD` is U+FFFD, the replacement character - i.e. the favicon bytes were run through a charset
conversion that destroyed them somewhere before reaching the client.

It is also self-perpetuating: `drawEntry` calls `getServerList().saveServerList()` right after
`prepareServerIcon`, so the broken icon is written to `servers.dat` and crashes the multiplayer
screen again on every launch.

## The fix

`ServerIconFix.sanitize(ServerData)` is called at the top of `prepareServerIcon`. It clears an icon
that netty would throw on (and strips a `data:image/png;base64,` prefix when a server sends one),
which makes the method take its own `icon == null` branch: the entry renders the "unknown server"
placeholder, and the following `saveServerList()` removes the broken icon from `servers.dat` for
good. Malformed PNG data is deliberately left alone - the existing `try`/`catch` already handles it.

`PatchJar` inserts the call as three instructions at the start of the method and adds
`ServerIconFix` to the jar; nothing else in the class or the jar is touched.

```
aload_0
getfield  net/minecraft/client/gui/ServerListEntryNormal.field_148301_e : Lnet/minecraft/client/multiplayer/ServerData;
invokestatic net/minecraft/client/gui/ServerIconFix.sanitize (Lnet/minecraft/client/multiplayer/ServerData;)V
```

### Without patching the jar

Delete the offending server from `servers.dat` (`.minecraft/servers.dat`), or delete the file and
re-add your servers. That clears the bad icon but does not stop it from happening again the next
time a server sends a mangled favicon.

## Usage

Needs a JDK and ASM 9.7 (`asm`, `asm-tree`, `asm-analysis`, `asm-util`).

```sh
ASM=asm-9.7.jar:asm-tree-9.7.jar:asm-analysis-9.7.jar:asm-util-9.7.jar

javac -cp client.jar -d out src/net/minecraft/client/gui/ServerIconFix.java
javac -cp client.jar:out:$ASM -d toolout tool/*.java

java -cp client.jar:out:toolout TestFix                      # reproduces the crash, checks the guard
java -cp toolout:$ASM PatchJar client.jar client-fixed.jar out
java -cp client-fixed.jar:toolout TestPatchedClass           # end-to-end check on the patched class
```

`PatchJar` runs ASM's `CheckClassAdapter` dataflow verification on the patched class before it
writes anything.

## Checks

- `TestFix` feeds the corrupt string to the jar's own netty and gets back the exact reported
  message, `bad Base64 input character at 1: 191 (decimal)`, then walks all 65535 chars to confirm
  the guard never accepts an icon netty would throw on.
- `TestPatchedClass` calls `prepareServerIcon` on a corrupt icon: unpatched it throws the reported
  `IllegalArgumentException`; patched the icon is cleared and the decode is never reached.
- Instruction-level comparison of the patched class against the original: 20 methods, one changed,
  `prepareServerIcon` 130 -> 133 instructions, the 130 original instructions unchanged.
