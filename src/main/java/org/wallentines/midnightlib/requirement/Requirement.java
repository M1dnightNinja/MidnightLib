package org.wallentines.midnightlib.requirement;

import org.wallentines.midnightlib.config.ConfigSection;
import org.wallentines.midnightlib.config.serialization.ConfigSerializer;
import org.wallentines.midnightlib.registry.Identifier;
import org.wallentines.midnightlib.registry.Registry;

import java.util.List;

public class Requirement<T> {

    private final RequirementType<T> type;
    private final String value;

    public Requirement(RequirementType<T> type, String value) {
        this.type = type;
        this.value = value;
    }

    public boolean check(T data) {
        return type.check(data, this, value);
    }

    public RequirementType<T> getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

    public static class RequirementSerializer<T> implements ConfigSerializer<Requirement<T>> {

        private final Registry<RequirementType<T>> registry;

        public RequirementSerializer(Registry<RequirementType<T>> registry) {
            this.registry = registry;
        }

        @SuppressWarnings("unchecked")
        @Override
        public Requirement<T> deserialize(ConfigSection section) {

            if(section.has("values", List.class)) {

                boolean any = section.has("any", Boolean.class) && section.getBoolean("any");
                return new MultiRequirement<T>(any, section.getListFiltered("values", (Class<Requirement<T>>) ((Class<?>)Requirement.class)));

            } else {

                return new Requirement<>(registry.get(section.get("type", Identifier.class)), section.getString("value"));

            }
        }

        @Override
        public ConfigSection serialize(Requirement<T> object) {

            if(object instanceof MultiRequirement) return MultiRequirement.serialize((MultiRequirement<?>) object);

            return new ConfigSection().with("type", object.type).with("value", object.value);

        }
    }

}
