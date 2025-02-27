package ai.giskard.domain;

import ai.giskard.utils.SimpleJSONStringAttributeConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@Entity(name = "callable_functions")
@DiscriminatorColumn(name = "callable_type", discriminatorType = DiscriminatorType.STRING)
public class Callable implements Serializable {
    @Id
    private UUID uuid;
    @Column(nullable = false)
    private String name;

    @Column
    private String displayName;
    @Column(nullable = false)
    private int version;
    private String module;

    @Column(columnDefinition = "VARCHAR")
    private String doc;
    @Column(columnDefinition = "VARCHAR")
    private String moduleDoc;
    @Column(nullable = false, columnDefinition = "VARCHAR")
    private String code;
    @Column(columnDefinition = "VARCHAR")
    @Convert(converter = SimpleJSONStringAttributeConverter.class)
    private List<String> tags;
    @OneToMany(mappedBy = "function", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FunctionArgument> args;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (!(o instanceof Callable callable)) return false;

        return new EqualsBuilder().append(uuid, callable.uuid).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(uuid).toHashCode();
    }
}
