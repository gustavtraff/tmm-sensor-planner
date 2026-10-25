package se.tmm.sensorplanner.plugin;

import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.cot.event.CotPoint;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import se.tmm.sensorplanner.core.PublicationPreparationReview;
import se.tmm.sensorplanner.core.TmmDraft;

/** SDK construction only: exercise the actual installed canary, then let the independent CLI check it. */
public final class PublicationFixtureCheck {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException();
        TmmDraft draft = PublicationProbeFixture.draft();
        CotEvent event = PublicationProbeFixture.create(new PublicationPreparationReview(draft).prepare(draft).metadata);
        require(PublicationProbeFixture.matches(event));
        Files.write(Paths.get(args[0]), event.toString().getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW);
        event.setPoint(new CotPoint(1, 1, CotPoint.UNKNOWN, CotPoint.UNKNOWN, CotPoint.UNKNOWN));
        require(!PublicationProbeFixture.matches(event));
        event = PublicationProbeFixture.create(new PublicationPreparationReview(draft).metadata);
        event.getDetail().getFirstChildByName(0, "tmmShareLab").setAttribute("owner", "changed");
        require(!PublicationProbeFixture.matches(event));
        event = PublicationProbeFixture.create(new PublicationPreparationReview(draft).metadata);
        event.getDetail().addChild(new CotDetail("tmmShareLab"));
        require(!PublicationProbeFixture.matches(event));
        TmmDraft changed = draft.edit("Changed", draft.type, draft.status, draft.owner, draft.deployedAt,
                draft.comment, draft.position, draft.bearingDeg, null, draft.updatedAt);
        try {
            PublicationProbeFixture.create(new PublicationPreparationReview(changed).metadata);
            throw new AssertionError("Non-fixture metadata accepted");
        } catch (IllegalArgumentException expected) { }
        System.out.println("Installed synthetic fixture construction and mismatch rejection passed. Nothing imported or sent.");
    }

    private static void require(boolean condition) {
        if (!condition) throw new AssertionError("Synthetic fixture check failed");
    }
}
