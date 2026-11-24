package fr.mariusbotkin.sdralert.core.detect;

/** Source de temps injectable (pour les tests). */
public interface Clock {
    long nowMs();

    Clock SYSTEM = new Clock() {
        @Override
        public long nowMs() {
            return System.currentTimeMillis();
        }
    };
}
