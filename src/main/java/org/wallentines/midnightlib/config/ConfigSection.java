package org.wallentines.midnightlib.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.internal.LinkedTreeMap;
import org.wallentines.midnightlib.config.serialization.ConfigSerializer;
import org.wallentines.midnightlib.config.serialization.InlineSerializer;

import java.util.*;

public class ConfigSection {
    private final ConfigRegistry reg;
    private final LinkedTreeMap<String, Object> entries = new LinkedTreeMap<>();

    private final HashMap<String, Object> cache = new HashMap<>();
    private final int cacheSize;

    public ConfigSection() {
        this(ConfigRegistry.INSTANCE, 10);
    }

    public ConfigSection(ConfigRegistry reg, int cacheSize) {
        this.reg = reg;
        this.cacheSize = cacheSize;
    }

    public <T> void set(String key, T obj) {
        // Remove an object
        if (obj == null) {

            this.entries.remove(key);

        } else {

            this.entries.put(key, serialize(obj));
        }
    }

    @SuppressWarnings("unchecked")
    private <T> Object serialize(T obj) {

        // Try to Serialize Map
        if (obj instanceof Map) {

            LinkedTreeMap<String, Object> out = new LinkedTreeMap<>();
            for (Map.Entry<?, ?> ent : ((Map<?, ?>) obj).entrySet()) {
                out.put(ent.getKey().toString(), serialize(ent.getValue()));
            }
            return out;

        // Try to serialize List elements
        } else if(obj instanceof Collection) {
            List<Object> serialized = new ArrayList<>();
            for(Object o : (Collection<?>) obj) {
                serialized.add(serialize(o));
            }
            return serialized;

        // Try to serialize as a ConfigSection
        } else if (reg != null && reg.canSerialize(obj.getClass())) {

            return reg.getSerializer((Class<T>) obj.getClass()).serialize(obj);

        // Try to serialize as a String
        } else if(reg != null && reg.canSerializeInline(obj.getClass())) {

            return reg.getInlineSerializer((Class<T>) obj.getClass()).serialize(obj);
        }
        // Return the raw data if we cannot serialize
        return obj;
    }

    public void setMap(String id, String keyLabel, String valueLabel, Map<?, ?> map) {

        List<ConfigSection> lst = new ArrayList<>();
        for(Map.Entry<?, ?> ent : map.entrySet()) {
            ConfigSection sec = new ConfigSection();
            sec.set(keyLabel, ent.getKey());
            sec.set(valueLabel, ent.getValue());
            lst.add(sec);
        }

        set(id, lst);
    }

    public ConfigSection with(String key, Object value) {

        set(key, value);
        return this;
    }

    public Object get(String key) {
        return this.entries.get(key);
    }

    public <T> T get(String key, Class<T> clazz) {
        Object out = this.get(key);

        return convert(out, clazz);
    }

    public Object getOrDefault(String key, Object def) {

        return entries.getOrDefault(key, def);
    }

    public <T> T getOrDefault(String key, T def, Class<T> clazz) {

        Object out = this.get(key);
        if(out != null) {
            try {
                return convert(out, clazz);
            } catch (Exception ex) {
                // Ignore
            }
        }

        return def;
    }

    public Iterable<String> getKeys() {
        return entries.keySet();
    }

    public Map<String, Object> getEntries() {
        return new HashMap<>(entries);
    }

    public boolean has(String key) {
        return this.entries.containsKey(key);
    }

    public <T> boolean has(String key, Class<T> clazz) {
        Object out = this.get(key);
        if(out == null) return false;

        return canConvert(out, clazz);
    }

    public String getString(String key) {
        return this.get(key, String.class);
    }

    public int getInt(String key) {
        return this.get(key, Number.class).intValue();
    }

    public float getFloat(String key) {
        return this.get(key, Number.class).floatValue();
    }

    public double getDouble(String key) {
        return this.get(key, Number.class).doubleValue();
    }

    public long getLong(String key) { return this.get(key, Number.class).longValue(); }

    public boolean getBoolean(String key) {

        return getBoolean(key, false);
    }

    public boolean getBoolean(String key, boolean def) {

        Object o = this.get(key);

        if(o == null) return def;

        if(o instanceof Boolean) {
            return (boolean) o;

        } else if(o instanceof Number) {
            return ((Number) o).intValue() != 0;

        } else if(o instanceof String) {
            return o.equals("true");
        }

        return convert(o, Boolean.class);
    }

    public List<?> getList(String key) {
        return this.get(key, List.class);
    }

    public List<String> getStringList(String key) {
        List<?> orig = this.getList(key);
        ArrayList<String> out = new ArrayList<>();
        for (Object o : orig) {
            out.add(o.toString());
        }
        return out;
    }

    public <T> List<T> getList(String key, Class<T> clazz) {

        List<?> lst = getList(key);
        List<T> out = new ArrayList<>(lst.size());
        for(Object o : lst) {
            out.add(convert(o, clazz));
        }

        return out;
    }

    public <T> List<T> getListFiltered(String key, Class<T> clazz) {

        List<?> lst = getList(key);
        List<T> out = new ArrayList<>();
        for(Object o : lst) {
            if(!canConvert(o, clazz)) continue;
            out.add(convert(o, clazz));
        }

        return out;
    }

    public ConfigSection getSection(String key) {
        return this.get(key, ConfigSection.class);
    }

    public ConfigSection getOrCreateSection(String key) {
        if(has(key, ConfigSection.class)) {
            return getSection(key);
        }
        ConfigSection sec = new ConfigSection();
        set(key, sec);
        return sec;
    }

    public void fill(ConfigSection other) {
        for(Map.Entry<String, Object> entry : other.getEntries().entrySet()) {
            if(!has(entry.getKey())) {
                set(entry.getKey(), entry.getValue());
            }
        }
    }

    public void fillOverwrite(ConfigSection other) {
        for(Map.Entry<String, Object> entry : other.getEntries().entrySet()) {
            set(entry.getKey(), entry.getValue());
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T convert(Object o, Class<T> clazz) {

        if(o == null) {
            throw new IllegalStateException("Unable to convert null to " + clazz.getName() + "!");
        }

        if(reg != null) {
            if(reg.canSerialize(clazz) && o instanceof ConfigSection) {
                ConfigSerializer<T> ser = reg.getSerializer(clazz);
                T ret = ser.deserialize((ConfigSection) o);
                if (ret == null) {
                    throw new IllegalStateException("Invalid Type! " + o.getClass().getName() + " cannot be converted to " + clazz.getName());
                }
                return ret;
            }
            if(reg.canSerializeInline(clazz)) {

                InlineSerializer<T> ser = reg.getInlineSerializer(clazz);
                T ret = ser.deserialize(o.toString());
                if (ret == null) {
                    throw new IllegalStateException("Invalid Type! " + o.getClass().getName() + " cannot be converted to " + clazz.getName());
                }

                return ret;
            }

        }

        if (!clazz.isAssignableFrom(o.getClass())) {
            throw new IllegalStateException("Invalid Type! " + o.getClass().getName() + " cannot be converted to " + clazz.getName());
        }

        return (T) o;
    }


    private <T> boolean canConvert(Object o, Class<T> clazz) {

        if(reg != null) {
            if(reg.canSerialize(clazz) && o instanceof ConfigSection) {
                ConfigSerializer<T> ser = reg.getSerializer(clazz);
                return ser.canDeserialize((ConfigSection) o);
            }
            if(reg.canSerializeInline(clazz)) {

                InlineSerializer<T> ser = reg.getInlineSerializer(clazz);
                return ser.canDeserialize(o.toString());
            }
        }

        return clazz.isAssignableFrom(o.getClass());
    }

    public JsonObject toJson() {

        return (JsonObject) toJsonElement(this);
    }

    @Override
    public String toString() {
        return toJson().toString();
    }

    private static JsonElement toJsonElement(Object obj) {

        if(obj instanceof ConfigSection) {

            ConfigSection sec = (ConfigSection) obj;

            JsonObject out = new JsonObject();
            for(String s : sec.getKeys()) {

                out.add(s, toJsonElement(sec.get(s)));
            }

            return out;

        } else if(obj instanceof List<?>) {

            List<?> lst = (List<?>) obj;

            JsonArray arr = new JsonArray();

            for (Object o : lst) {
                arr.add(toJsonElement(o));
            }
            return arr;

        } else if(obj instanceof Number) {

            return new JsonPrimitive((Number) obj);

        } else if(obj instanceof Boolean) {

            return new JsonPrimitive((Boolean) obj);

        } else {

            return new JsonPrimitive(obj.toString());
        }
    }

}
