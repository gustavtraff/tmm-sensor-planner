package se.tmm.sensorplanner.plugin.images;

import android.annotation.TargetApi;
import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import se.tmm.sensorplanner.core.ImageOfferCodec;

/** Confirmed synthetic snapshot files only. No provider URL, native publication or automatic deletion. */
@TargetApi(26)
public final class ImageSnapshotExports {
    public static final int MAX_FILES=16;
    public static final long MAX_BYTES=256L*1024*1024;
    private final Path root;
    public ImageSnapshotExports(Path root){this.root=Objects.requireNonNull(root);}
    public String export(ImagePublicationReview.Prepared prepared,BooleanSupplier active)throws IOException {
        try {
            require(active);Files.createDirectories(root);
            if(Files.isSymbolicLink(root))throw new IOException();
            try(FileChannel lock=FileChannel.open(root.resolve("exports.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);
                FileLock held=lock.tryLock()) {
                if(held==null)throw new IOException();int count=0;long bytes=0;
                try(DirectoryStream<Path> entries=Files.newDirectoryStream(root)) {
                    for(Path file:entries) {
                        require(active);String name=file.getFileName().toString();if(name.equals("exports.lock"))continue;
                        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||!name.matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}\\.(?:tmmi|pending)"))throw new IOException();
                        if(name.endsWith(".pending"))throw new IOException();
                        if(++count>=MAX_FILES)throw new IOException();bytes+=Files.size(file);if(bytes>MAX_BYTES)throw new IOException();
                    }
                }
                long added=20L+prepared.descriptor().length+prepared.offer.pngLength;
                if(added>MAX_BYTES-bytes)throw new IOException();
                String id=UUID.randomUUID().toString();Path pending=root.resolve(id+".pending"),ready=root.resolve(id+".tmmi");
                require(active);
                try(FileChannel output=FileChannel.open(pending,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)) {
                    OutputStream stream=Channels.newOutputStream(output);
                    prepared.writeContainer(new OutputStream(){
                        public void write(int value)throws IOException{require(active);stream.write(value);}
                        public void write(byte[] value,int offset,int length)throws IOException {
                            while(length>0){require(active);int n=Math.min(length,8192);stream.write(value,offset,n);offset+=n;length-=n;}
                        }
                    });
                    output.force(true);
                }
                require(active);
                try(InputStream input=Files.newInputStream(pending,LinkOption.NOFOLLOW_LINKS)) {
                    ReceivedImagePayload actual=ImageContainerCodec.read(input);
                    if(!Arrays.equals(ImageOfferCodec.encode(actual.offer),prepared.descriptor())
                            ||!Arrays.equals(actual.image.bytes(),prepared.png()))throw new IOException();
                }
                require(active);
                if(!Files.notExists(ready,LinkOption.NOFOLLOW_LINKS))throw new IOException();
                Files.move(pending,ready,StandardCopyOption.ATOMIC_MOVE);
                require(active);
                try(InputStream input=Files.newInputStream(ready,LinkOption.NOFOLLOW_LINKS)) {
                    ReceivedImagePayload actual=ImageContainerCodec.read(input);
                    if(!Arrays.equals(ImageOfferCodec.encode(actual.offer),prepared.descriptor())||!Arrays.equals(actual.image.bytes(),prepared.png()))throw new IOException();
                }
                require(active);return ready.getFileName().toString();
            }
        }catch(Exception unknown){throw new IOException("Image export outcome is unconfirmed; inspect Downloads before retrying");}
        catch(OutOfMemoryError exhausted){throw new se.tmm.sensorplanner.images.ImagePreparationException(se.tmm.sensorplanner.images.ImagePreparationException.Reason.LOW_MEMORY);}
    }
    private static void require(BooleanSupplier active)throws IOException {
        if(active==null||Thread.currentThread().isInterrupted()||!active.getAsBoolean())throw new IOException();
    }
}
