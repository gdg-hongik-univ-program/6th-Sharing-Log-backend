package gdg.sharinglog.service.rotation;

@FunctionalInterface
public interface DecisionSeedGenerator {

    long nextSeed();
}
