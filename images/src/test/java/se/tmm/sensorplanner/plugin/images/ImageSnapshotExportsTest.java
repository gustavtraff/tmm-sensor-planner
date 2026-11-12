package se.tmm.sensorplanner.plugin.images;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.*;
import se.tmm.sensorplanner.core.*;
import se.tmm.sensorplanner.images.LocalImages;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={26,35},manifest=Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ImageSnapshotExportsTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    @Test public void exportUsesActualFrozenBytesAndPreservesPrivateSource()throws Exception {
        Fixture f=new Fixture();String name=f.exports.export(f.prepared,()->true);
        assertTrue(name.matches("[0-9a-f-]{36}\\.tmmi"));
        try(InputStream in=Files.newInputStream(f.exportsRoot.resolve(name))) {
            ReceivedImagePayload received=ImageContainerCodec.read(in);
            assertEquals(f.prepared.offer.deliveryId,received.offer.deliveryId);assertTrue(f.prepared.offer.sameBinding(received.offer));assertArrayEquals(f.prepared.png(),received.image.bytes());
        }
        assertArrayEquals(f.original,Files.readAllBytes(f.source));assertEquals(0,count(f.exportsRoot,".pending"));
    }
    @Test public void cancellationBeforeWriteCreatesNoDirectoryOrExport()throws Exception {
        Fixture f=new Fixture();assertThrows(IOException.class,()->f.exports.export(f.prepared,()->false));assertFalse(Files.exists(f.exportsRoot));assertArrayEquals(f.original,Files.readAllBytes(f.source));
    }
    @Test public void cancellationDuringWriteKeepsPartialAndBlocksAdmissionWithoutDeletingIt()throws Exception {
        Fixture f=new Fixture();assertThrows(IOException.class,()->f.exports.export(f.prepared,()->{
            if(!Files.exists(f.exportsRoot))return true;
            try(java.util.stream.Stream<Path> paths=Files.list(f.exportsRoot)) {
                return paths.noneMatch(p->{try{return p.toString().endsWith(".pending")&&Files.size(p)>0;}catch(IOException e){return true;}});
            }catch(IOException e){return false;}
        }));
        assertEquals(0,count(f.exportsRoot,".tmmi"));assertEquals(1,count(f.exportsRoot,".pending"));
        assertThrows(IOException.class,()->f.exports.export(f.prepared,()->true));assertEquals(1,count(f.exportsRoot,".pending"));
    }
    @Test public void cancellationAfterRenameDoesNotClaimRollbackOrDeleteCompletedFile()throws Exception {
        Fixture f=new Fixture();assertThrows(IOException.class,()->f.exports.export(f.prepared,()->{
            try{return !Files.exists(f.exportsRoot)||count(f.exportsRoot,".tmmi")==0;}catch(Exception failure){return false;}
        }));
        assertEquals(1,count(f.exportsRoot,".tmmi"));try(java.util.stream.Stream<Path> paths=Files.list(f.exportsRoot)) {
            Path file=paths.filter(p->p.toString().endsWith(".tmmi")).findFirst().get();try(InputStream in=Files.newInputStream(file)){assertArrayEquals(f.prepared.png(),ImageContainerCodec.read(in).image.bytes());}
        }
    }
    @Test public void countByteQuotaUnknownFilesAndLinksRefuseWithoutOverwriteOrEviction()throws Exception {
        Fixture f=new Fixture();for(int n=0;n<16;n++)f.exports.export(f.prepared,()->true);assertEquals(16,count(f.exportsRoot,".tmmi"));assertThrows(IOException.class,()->f.exports.export(f.prepared,()->true));assertEquals(16,count(f.exportsRoot,".tmmi"));
        Fixture sparse=new Fixture();Files.createDirectories(sparse.exportsRoot);Path large=sparse.exportsRoot.resolve(UUID.randomUUID()+".tmmi");try(RandomAccessFile file=new RandomAccessFile(large.toFile(),"rw")){file.setLength(ImageSnapshotExports.MAX_BYTES);}
        assertThrows(IOException.class,()->sparse.exports.export(sparse.prepared,()->true));assertEquals(ImageSnapshotExports.MAX_BYTES,Files.size(large));
        Fixture foreign=new Fixture();Files.createDirectories(foreign.exportsRoot);Path unknown=foreign.exportsRoot.resolve("do-not-touch.txt");Files.write(unknown,new byte[]{7});assertThrows(IOException.class,()->foreign.exports.export(foreign.prepared,()->true));assertArrayEquals(new byte[]{7},Files.readAllBytes(unknown));
        Fixture linked=new Fixture();Path other=temp.newFolder().toPath();Files.createSymbolicLink(linked.exportsRoot,other);assertThrows(IOException.class,()->linked.exports.export(linked.prepared,()->true));try(java.util.stream.Stream<Path> paths=Files.list(other)){assertEquals(0,paths.count());}
    }
    private static long count(Path root,String suffix)throws IOException{try(java.util.stream.Stream<Path> files=Files.list(root)){return files.filter(p->p.getFileName().toString().endsWith(suffix)).count();}}
    private final class Fixture {
        final Path source,exportsRoot;final byte[] original;final ImageSnapshotExports exports;final ImagePublicationReview.Prepared prepared;
        Fixture()throws Exception {
            Path root=temp.newFolder().toPath(),owned=root.resolve("owned");Files.createDirectory(owned);source=owned.resolve("00000000-0000-4000-8000-000000000208.png");
            try(InputStream in=ImageSnapshotExportsTest.class.getResourceAsStream("/h1.tmmi")) {
                ReceivedImagePayload p=ImageContainerCodec.read(in);original=p.image.bytes();Files.write(source,original);ShareReview.Metadata m=p.offer.metadata;
                TmmDraft d=new TmmDraft(m.uid,m.displayId,m.type,m.status,m.owner,m.createdAt,m.updatedAt,m.deployedAt,m.comment,m.position,m.bearingDeg,source.getFileName().toString(),1);
                LocalV2.Snapshot snapshot=new LocalV2.Snapshot(UUID.randomUUID(),Collections.singletonList(LocalV2.State.active(1,d)),Collections.emptyList());
                LocalImages images=new LocalImages(owned);ImagePublicationReview review=new ImagePublicationReview(snapshot.select(d.uid),"synthetic-destination","Synthetic feed",images);prepared=review.confirm(snapshot,"synthetic-destination","Synthetic feed",images);
            }
            exportsRoot=root.resolve("Downloads");exports=new ImageSnapshotExports(exportsRoot);
        }
    }
}
