/*
 * Copyright Cedar Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.cedarpolicy;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static com.cedarpolicy.TestUtil.loadSchemaResource;
import static com.cedarpolicy.TestUtil.loadCedarSchemaResource;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.cedarpolicy.model.AuthorizationRequest;
import com.cedarpolicy.model.AuthorizationResponse;
import com.cedarpolicy.model.AuthorizationSuccessResponse.Decision;
import com.cedarpolicy.model.ValidationRequest;
import com.cedarpolicy.model.ValidationResponse;
import com.cedarpolicy.model.exception.InternalException;
import com.cedarpolicy.model.policy.PolicySet;
import com.cedarpolicy.model.schema.Schema;
import com.cedarpolicy.model.schema.Schema.JsonOrCedar;
import com.cedarpolicy.value.EntityTypeName;
import com.cedarpolicy.value.EntityUID;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class SchemaTests {
    @Test
    public void parseJsonSchema() {
        assertDoesNotThrow(() -> {
            Schema.parse(JsonOrCedar.Json, "{}");
            Schema.parse(JsonOrCedar.Json, """
                    {
                        "Foo::Bar": {
                            "entityTypes": {},
                            "actions": {}
                        }
                    }
                    """);
            Schema.parse(JsonOrCedar.Json, """
                    {
                        "": {
                            "entityTypes": {
                                "User": {
                                    "shape": {
                                        "type": "Record",
                                        "attributes": {
                                            "name": {
                                                "type": "String",
                                                "required": true
                                            },
                                            "age": {
                                                "type": "Long",
                                                "required": false
                                            }
                                        }
                                    }
                                },
                                "Photo": {
                                    "memberOfTypes": [ "Album" ]
                                },
                                "Album": {}
                            },
                            "actions": {
                                "view": {
                                    "appliesTo": {
                                        "principalTypes": ["User"],
                                        "resourceTypes": ["Photo", "Album"]
                                    }
                                }
                            }
                        }
                    }
                    """);
        });
        assertThrows(Exception.class, () -> {
            Schema.parse(JsonOrCedar.Json, "{\"foo\": \"bar\"}");
            Schema.parse(JsonOrCedar.Json, "namespace Foo::Bar;");
        });
    }

    @Test
    public void parseCedarSchema() {
        assertDoesNotThrow(() -> {
            Schema.parse(JsonOrCedar.Cedar, "");
            Schema.parse(JsonOrCedar.Cedar, "namespace Foo::Bar {}");
            Schema.parse(JsonOrCedar.Cedar, """
                    entity User = {
                        name: String,
                        age?: Long,
                    };
                    entity Photo in Album;
                    entity Album;
                    action view
                      appliesTo { principal: [User], resource: [Album, Photo] };
                    """);
        });
        assertThrows(Exception.class, () -> {
            Schema.parse(JsonOrCedar.Cedar, """
                    {
                        "Foo::Bar": {
                            "entityTypes" {},
                            "actions": {}
                        }
                    }
                    """);
            Schema.parse(JsonOrCedar.Cedar, "namspace Foo::Bar;");
        });
    }

    @Nested
    @DisplayName("toCedarFormat Tests")
    class ToCedarFormatTests {

        @Test
        @DisplayName("Should return the same Cedar schema text")
        void testFromCedar() throws InternalException {
            String cedarSchema = "entity User;";
            Schema cedarSchemaObj = new Schema(cedarSchema);
            String result = cedarSchemaObj.toCedarFormat();
            assertNotNull(result, "Result should not be null");
            assertEquals(cedarSchema, result, "Should return the original Cedar schema");
        }

        @Test
        @DisplayName("Should convert JSON schema to Cedar format")
        void testFromJson() throws InternalException {
            String jsonSchema = """
                    {
                        "": {
                            "entityTypes": {
                                "User": {}
                            },
                            "actions": {}
                        }
                    }
                    """;
            Schema jsonSchemaObj = Schema.parse(JsonOrCedar.Json, jsonSchema);
            String result = jsonSchemaObj.toCedarFormat();

            assertNotNull(result, "Result should not be null");
            String expectedCedar = "entity User;";
            assertEquals(expectedCedar, result.trim(), "Converted Cedar should match expected format");
        }

        @Test
        @DisplayName("Should throw IllegalStateException for empty schema")
        void testEmptySchema() {
            Schema emptySchema = new Schema(JsonOrCedar.Cedar, Optional.empty(), Optional.empty());
            Exception exception = assertThrows(IllegalStateException.class, emptySchema::toCedarFormat);
            assertEquals("No schema found", exception.getMessage());
        }

        @Test
        @DisplayName("Should throw exception for malformed JSON schema")
        void testMalformedSchema() {
            String malformedJson = """
                    {
                        "": {
                            "entityMalformedTypes": {
                                "User": {}
                            },
                            "actions": {}
                        }
                    }
                    """;
            Schema malformedSchema = new Schema(JsonOrCedar.Json, Optional.of(malformedJson), Optional.empty());
            assertNotNull(malformedSchema.schemaJson);
            assertThrows(InternalException.class, malformedSchema::toCedarFormat);
        }
    }

    @Nested
    @DisplayName("toJsonFormat Tests")
    class ToJsonFormatTests {

        @Test
        @DisplayName("Should convert Cedar schema to JSON format")
        void testFromCedar() throws Exception {
            String cedarSchema = "entity User;";
            Schema cedarSchemaObj = new Schema(cedarSchema);
            JsonNode result = cedarSchemaObj.toJsonFormat();

            String expectedJson = "{\"\":{\"entityTypes\":{\"User\":{}},\"actions\":{}}}";
            JsonNode expectedNode = new ObjectMapper().readTree(expectedJson);

            assertNotNull(result, "Result should not be null");
            assertEquals(expectedNode, result, "JSON should match expected structure");
        }

        @Test
        @DisplayName("Should return the same JSON schema object")
        void testFromJson() throws Exception {
            String jsonSchema = """
                    {
                        "": {
                            "entityTypes": {
                                "User": {}
                            },
                            "actions": {}
                        }
                    }
                    """;
            Schema jsonSchemaObj = Schema.parse(JsonOrCedar.Json, jsonSchema);
            JsonNode result = jsonSchemaObj.toJsonFormat();

            ObjectMapper mapper = new ObjectMapper();
            JsonNode expectedNode = mapper.readTree(jsonSchema);

            assertNotNull(result, "Result should not be null");
            assertEquals(expectedNode, result, "JSON should match the original schema");
        }

        @Test
        @DisplayName("Should throw IllegalStateException for empty schema")
        void testEmptySchema() {
            Schema emptySchema = new Schema(JsonOrCedar.Cedar, Optional.empty(), Optional.empty());
            Exception exception = assertThrows(IllegalStateException.class, emptySchema::toJsonFormat);
            assertEquals("No schema found", exception.getMessage());
        }

        @Test
        @DisplayName("Should throw exception for malformed Cedar schema")
        void testMalformedSchema() {
            String malformedCedar = "entty User";
            Schema malformedSchema = new Schema(JsonOrCedar.Cedar, Optional.empty(), Optional.of(malformedCedar));
            assertThrows(InternalException.class, malformedSchema::toJsonFormat);
        }
    }

    @Nested
    @DisplayName("Enum Schema Tests")
    class EnumSchemaTests {

        @Test
        void testParseJsonEnumSchema() {
            assertDoesNotThrow(() -> {
                Schema enumSchema = loadSchemaResource("/enum_schema.json");
                assertNotNull(enumSchema, "Enum schema should not be null");
            });
        }

        @Test
        @DisplayName("Should parse Cedar schema with enum entities")
        void testParseCedarEnumSchema() {
            assertDoesNotThrow(() -> {
                Schema enumSchema = loadCedarSchemaResource("/enum_schema.cedarschema");
                assertNotNull(enumSchema, "Enum schema should not be null");
            });
        }

        @Test
        void testRejectEmptyEnums() {
            // Test Cedar format empty enum
            assertThrows(Exception.class, () -> {
                Schema.parse(JsonOrCedar.Cedar, "entity Color enum [];");
            });

            // Test JSON format empty enum
            assertThrows(Exception.class, () -> {
                Schema.parse(JsonOrCedar.Json, """
                        {
                            "": {
                                "entityTypes": {
                                    "Color": {
                                        "enum": []
                                    }
                                },
                                "actions": {}
                            }
                        }
                        """);
            });
        }

        @Test
        void testEnumSchemaFormatConversion() throws Exception {
            // Test Cedar to JSON conversion
            Schema cedarEnumSchema = Schema.parse(JsonOrCedar.Cedar, """
                    entity Color enum ["Red", "Blue", "Green"];
                    entity User;
                    action view appliesTo { principal: [User], resource: [User] };
                    """);

            JsonNode jsonResult = cedarEnumSchema.toJsonFormat();
            assertNotNull(jsonResult, "JSON conversion result should not be null");

            // Test JSON to Cedar conversion
            String jsonEnumSchema = """
                    {
                        "": {
                            "entityTypes": {
                                "Color": {
                                    "enum": ["Red", "Blue", "Green"]
                                },
                                "User": {}
                            },
                            "actions": {
                                "view": {
                                    "appliesTo": {
                                        "principalTypes": ["User"],
                                        "resourceTypes": ["User"]
                                    }
                                }
                            }
                        }
                    }
                    """;
            Schema jsonSchemaObj = Schema.parse(JsonOrCedar.Json, jsonEnumSchema);
            String cedarResult = jsonSchemaObj.toCedarFormat();
            assertNotNull(cedarResult, "Cedar conversion result should not be null");
        }
    }

    @Nested
    @DisplayName("fromFragments Tests")
    class FromFragmentsTests {
        // Each fragment refers to types declared in the other one.
        private final Schema usersFragment = new Schema(JsonOrCedar.Json, Optional.of("""
                {
                    "App": {
                        "entityTypes": {
                            "User": { "memberOfTypes": ["Group"] },
                            "Group": {}
                        },
                        "actions": {}
                    }
                }
                """), Optional.empty());
        private final Schema documentsFragment = new Schema("""
                namespace App {
                    entity Document;
                    action view appliesTo { principal: [User], resource: [Document] };
                }
                """);

        private final AuthorizationEngine engine = new BasicAuthorizationEngine();

        private EntityUID uid(String type, String id) {
            return new EntityUID(EntityTypeName.parse(type).get(), id);
        }

        @Test
        void testCombinesJsonAndCedarFragments() throws Exception {
            Schema schema = Schema.fromFragments(List.of(usersFragment, documentsFragment));

            assertEquals(JsonOrCedar.Json, schema.type);
            JsonNode entityTypes = schema.toJsonFormat().get("App").get("entityTypes");
            assertTrue(entityTypes.has("User"));
            assertTrue(entityTypes.has("Group"));
            assertTrue(entityTypes.has("Document"));
            assertTrue(schema.toJsonFormat().get("App").get("actions").has("view"));
            assertDoesNotThrow(() -> Schema.parse(JsonOrCedar.Json, schema.schemaJson.get().toString()));
        }

        @Test
        void testValidatesPoliciesAgainstCombinedSchema() throws Exception {
            Schema schema = Schema.fromFragments(List.of(usersFragment, documentsFragment));

            PolicySet valid = PolicySet.parsePolicies(
                    "permit(principal in App::Group::\"admins\", action == App::Action::\"view\", resource);");
            ValidationResponse validResponse = engine.validate(new ValidationRequest(schema, valid));
            assertTrue(validResponse.success.get().validationErrors.isEmpty());

            PolicySet invalid = PolicySet.parsePolicies(
                    "permit(principal, action == App::Action::\"edit\", resource);");
            ValidationResponse invalidResponse = engine.validate(new ValidationRequest(schema, invalid));
            assertFalse(invalidResponse.success.get().validationErrors.isEmpty());
        }

        @Test
        void testAuthorizesWithCombinedSchema() throws Exception {
            Schema schema = Schema.fromFragments(List.of(usersFragment, documentsFragment));
            PolicySet policies = PolicySet.parsePolicies("permit(principal, action, resource);");
            AuthorizationRequest request = new AuthorizationRequest(uid("App::User", "alice"),
                    uid("App::Action", "view"), uid("App::Document", "doc"), Optional.of(new HashMap<>()),
                    Optional.of(schema), true);

            AuthorizationResponse uncached = engine.isAuthorized(request, policies, new HashSet<>());
            assertEquals(Decision.Allow, uncached.success.get().getDecision());

            schema.cache();
            policies.cache();
            AuthorizationResponse cached = engine.isAuthorized(request, policies, new HashSet<>());
            assertEquals(Decision.Allow, cached.success.get().getDecision());

            // Request validation uses the combined schema: Group is not a valid principal for view.
            AuthorizationRequest invalidRequest = new AuthorizationRequest(uid("App::Group", "admins"),
                    uid("App::Action", "view"), uid("App::Document", "doc"), Optional.of(new HashMap<>()),
                    Optional.of(schema), true);
            AuthorizationResponse rejected = engine.isAuthorized(invalidRequest, policies, new HashSet<>());
            assertTrue(rejected.errors.isPresent());
        }

        @Test
        void testSingleAndEmptyFragmentLists() throws Exception {
            Schema single = Schema.fromFragments(List.of(documentsFragment, new Schema("namespace App { entity User; }")));
            assertTrue(single.toJsonFormat().get("App").get("entityTypes").has("User"));
            assertEquals("{}", Schema.fromFragments(List.of()).schemaJson.get().toString());
        }

        @Test
        void testDuplicateDeclarationFails() {
            InternalException e = assertThrows(InternalException.class, () -> Schema.fromFragments(List.of(
                    new Schema("namespace App { entity User; }"), new Schema("namespace App { entity User; }"))));
            assertTrue(e.getMessage().contains("App::User"), e.getMessage());
        }

        @Test
        void testUndeclaredTypeFails() {
            // documentsFragment refers to App::User, which only usersFragment declares.
            InternalException e = assertThrows(InternalException.class,
                    () -> Schema.fromFragments(List.of(documentsFragment)));
            assertTrue(e.getMessage().contains("failed to resolve type: User"), e.getMessage());
        }

        @Test
        void testMalformedFragmentFails() {
            assertThrows(InternalException.class,
                    () -> Schema.fromFragments(List.of(usersFragment, new Schema("not a schema"))));
        }

        @Test
        void testNullInputs() {
            assertThrows(NullPointerException.class, () -> Schema.fromFragments(null));
            assertThrows(NullPointerException.class,
                    () -> Schema.fromFragments(Arrays.asList(usersFragment, null)));
        }
    }
}
