-- 把物模型路径中的 code 转成 RFC 6901 JSON Pointer 片段。
CREATE FUNCTION sca4dj_iot_escape_pointer(value TEXT)
RETURNS TEXT
LANGUAGE SQL
IMMUTABLE
STRICT
AS $$
    SELECT replace(replace(value, '~', '~0'), '/', '~1')
$$;

-- 合并同一 Locale 的路径文本；重复路径说明旧模型存在重复 code，迁移必须终止。
CREATE FUNCTION sca4dj_iot_merge_i18n(current_fields JSONB, incoming_fields JSONB, source_path TEXT)
RETURNS JSONB
LANGUAGE plpgsql
IMMUTABLE
STRICT
AS $$
DECLARE
    field_key TEXT;
BEGIN
    FOR field_key IN SELECT jsonb_object_keys(incoming_fields)
    LOOP
        IF current_fields ? field_key THEN
            RAISE EXCEPTION '旧物模型包含重复展示路径: %，来源: %', field_key, source_path;
        END IF;
    END LOOP;
    RETURN current_fields || incoming_fields;
END
$$;

-- 递归移除旧物模型展示字段，并生成 zh-CN 路径翻译。
CREATE FUNCTION sca4dj_iot_convert_model_node(node JSONB, node_kind TEXT, node_path TEXT)
RETURNS JSONB
LANGUAGE plpgsql
IMMUTABLE
STRICT
AS $$
DECLARE
    allowed_fields TEXT[];
    code TEXT;
    label TEXT;
    child JSONB;
    child_code TEXT;
    child_path TEXT;
    child_result JSONB;
    converted_array JSONB;
    converted_model JSONB;
    converted_i18n JSONB := '{}'::JSONB;
    seen_codes JSONB;
BEGIN
    IF jsonb_typeof(node) <> 'object' THEN
        RAISE EXCEPTION '旧物模型节点必须是 object: %', node_path;
    END IF;
    IF EXISTS (SELECT 1 FROM jsonb_each(node) item WHERE item.value = 'null'::JSONB) THEN
        RAISE EXCEPTION '旧物模型节点不能包含 null: %', node_path;
    END IF;

    allowed_fields := CASE node_kind
        WHEN 'MODEL' THEN ARRAY['formatVersion', 'productCode', 'version', 'displayName', 'properties', 'events', 'commands']
        WHEN 'PROPERTY' THEN ARRAY['code', 'displayName', 'valueType', 'access', 'telemetry', 'acknowledgementPolicy', 'retryPolicy']
        WHEN 'EVENT' THEN ARRAY['code', 'delivery', 'outputs']
        WHEN 'COMMAND' THEN ARRAY['code', 'inputs', 'acknowledgementPolicy', 'retryPolicy']
        WHEN 'FIELD' THEN ARRAY['code', 'valueType']
        WHEN 'TYPE' THEN ARRAY['kind', 'minimum', 'maximum', 'scale', 'unit', 'maximumLength', 'maximumItems', 'enumValues', 'requiredFields', 'optionalFields', 'elementType']
        WHEN 'ENUM' THEN ARRAY['code', 'displayName']
        ELSE NULL
    END;
    IF allowed_fields IS NULL THEN
        RAISE EXCEPTION '未知旧物模型节点类型: %', node_kind;
    END IF;
    IF (node - allowed_fields) <> '{}'::JSONB THEN
        RAISE EXCEPTION '旧物模型节点包含未知字段: %，路径: %', node - allowed_fields, node_path;
    END IF;

    IF node_kind = 'MODEL' THEN
        IF jsonb_typeof(node -> 'formatVersion') <> 'number' OR node ->> 'formatVersion' <> '1' THEN
            RAISE EXCEPTION '旧物模型 formatVersion 必须为 1: %', node_path;
        END IF;
        IF jsonb_typeof(node -> 'productCode') <> 'string'
                OR btrim(node ->> 'productCode') = ''
                OR jsonb_typeof(node -> 'version') <> 'number' THEN
            RAISE EXCEPTION '旧物模型 productCode 或 version 不合法: %', node_path;
        END IF;
        code := node ->> 'productCode';
        label := CASE
            WHEN jsonb_typeof(node -> 'displayName') = 'string' AND btrim(node ->> 'displayName') <> ''
                THEN node ->> 'displayName'
            WHEN node ? 'displayName' THEN NULL
            ELSE code
        END;
        IF label IS NULL THEN
            RAISE EXCEPTION '旧物模型 displayName 不合法: %', node_path;
        END IF;
        converted_model := jsonb_set(node - 'displayName', '{formatVersion}', '2'::JSONB);
        converted_i18n := jsonb_build_object('/displayName', label);

        IF jsonb_typeof(node -> 'properties') <> 'array'
                OR jsonb_typeof(node -> 'events') <> 'array'
                OR jsonb_typeof(node -> 'commands') <> 'array' THEN
            RAISE EXCEPTION '旧物模型 properties、events 和 commands 必须是 array: %', node_path;
        END IF;

        converted_array := '[]'::JSONB;
        seen_codes := '{}'::JSONB;
        FOR child IN SELECT value FROM jsonb_array_elements(node -> 'properties') WITH ORDINALITY AS items(value, position) ORDER BY position
        LOOP
            IF jsonb_typeof(child -> 'code') <> 'string' OR btrim(child ->> 'code') = '' THEN
                RAISE EXCEPTION '旧物模型属性 code 不合法: /properties';
            END IF;
            child_code := child ->> 'code';
            IF seen_codes ? child_code THEN
                RAISE EXCEPTION '旧物模型属性 code 重复: %', child_code;
            END IF;
            seen_codes := seen_codes || jsonb_build_object(child_code, true);
            child_path := '/properties/' || sca4dj_iot_escape_pointer(child_code);
            child_result := sca4dj_iot_convert_model_node(child, 'PROPERTY', child_path);
            converted_array := converted_array || jsonb_build_array(child_result -> 'model');
            converted_i18n := sca4dj_iot_merge_i18n(converted_i18n, child_result -> 'i18n', child_path);
        END LOOP;
        converted_model := jsonb_set(converted_model, '{properties}', converted_array);

        converted_array := '[]'::JSONB;
        seen_codes := '{}'::JSONB;
        FOR child IN SELECT value FROM jsonb_array_elements(node -> 'events') WITH ORDINALITY AS items(value, position) ORDER BY position
        LOOP
            IF jsonb_typeof(child -> 'code') <> 'string' OR btrim(child ->> 'code') = '' THEN
                RAISE EXCEPTION '旧物模型事件 code 不合法: /events';
            END IF;
            child_code := child ->> 'code';
            IF seen_codes ? child_code THEN
                RAISE EXCEPTION '旧物模型事件 code 重复: %', child_code;
            END IF;
            seen_codes := seen_codes || jsonb_build_object(child_code, true);
            child_path := '/events/' || sca4dj_iot_escape_pointer(child_code);
            child_result := sca4dj_iot_convert_model_node(child, 'EVENT', child_path);
            converted_array := converted_array || jsonb_build_array(child_result -> 'model');
            converted_i18n := sca4dj_iot_merge_i18n(converted_i18n, child_result -> 'i18n', child_path);
        END LOOP;
        converted_model := jsonb_set(converted_model, '{events}', converted_array);

        converted_array := '[]'::JSONB;
        seen_codes := '{}'::JSONB;
        FOR child IN SELECT value FROM jsonb_array_elements(node -> 'commands') WITH ORDINALITY AS items(value, position) ORDER BY position
        LOOP
            IF jsonb_typeof(child -> 'code') <> 'string' OR btrim(child ->> 'code') = '' THEN
                RAISE EXCEPTION '旧物模型命令 code 不合法: /commands';
            END IF;
            child_code := child ->> 'code';
            IF seen_codes ? child_code THEN
                RAISE EXCEPTION '旧物模型命令 code 重复: %', child_code;
            END IF;
            seen_codes := seen_codes || jsonb_build_object(child_code, true);
            child_path := '/commands/' || sca4dj_iot_escape_pointer(child_code);
            child_result := sca4dj_iot_convert_model_node(child, 'COMMAND', child_path);
            converted_array := converted_array || jsonb_build_array(child_result -> 'model');
            converted_i18n := sca4dj_iot_merge_i18n(converted_i18n, child_result -> 'i18n', child_path);
        END LOOP;
        converted_model := jsonb_set(converted_model, '{commands}', converted_array);

    ELSIF node_kind = 'PROPERTY' THEN
        IF jsonb_typeof(node -> 'code') <> 'string'
                OR btrim(node ->> 'code') = ''
                OR jsonb_typeof(node -> 'valueType') <> 'object'
                OR jsonb_typeof(node -> 'access') <> 'string'
                OR (node ? 'telemetry' AND jsonb_typeof(node -> 'telemetry') <> 'boolean')
                OR (node ? 'acknowledgementPolicy' AND jsonb_typeof(node -> 'acknowledgementPolicy') <> 'string')
                OR (node ? 'retryPolicy' AND jsonb_typeof(node -> 'retryPolicy') <> 'string') THEN
            RAISE EXCEPTION '旧物模型属性字段不合法: %', node_path;
        END IF;
        code := node ->> 'code';
        label := CASE
            WHEN jsonb_typeof(node -> 'displayName') = 'string' AND btrim(node ->> 'displayName') <> ''
                THEN node ->> 'displayName'
            WHEN node ? 'displayName' THEN NULL
            ELSE code
        END;
        IF label IS NULL THEN
            RAISE EXCEPTION '旧物模型属性 displayName 不合法: %', node_path;
        END IF;
        converted_model := node - 'displayName';
        IF NOT converted_model ? 'telemetry' THEN
            converted_model := converted_model || jsonb_build_object('telemetry', false);
        END IF;
        converted_i18n := jsonb_build_object(node_path || '/displayName', label);
        child_result := sca4dj_iot_convert_model_node(node -> 'valueType', 'TYPE', node_path || '/valueType');
        converted_model := jsonb_set(converted_model, '{valueType}', child_result -> 'model');
        converted_i18n := sca4dj_iot_merge_i18n(converted_i18n, child_result -> 'i18n', node_path);

    ELSIF node_kind = 'EVENT' OR node_kind = 'COMMAND' THEN
        IF jsonb_typeof(node -> 'code') <> 'string' OR btrim(node ->> 'code') = '' THEN
            RAISE EXCEPTION '旧物模型事件或命令 code 不合法: %', node_path;
        END IF;
        code := node ->> 'code';
        converted_model := node;
        converted_i18n := jsonb_build_object(node_path || '/displayName', code);
        IF node_kind = 'EVENT' THEN
            IF jsonb_typeof(node -> 'delivery') <> 'string' OR jsonb_typeof(node -> 'outputs') <> 'array' THEN
                RAISE EXCEPTION '旧物模型事件字段不合法: %', node_path;
            END IF;
            child_path := 'outputs';
        ELSE
            IF jsonb_typeof(node -> 'inputs') <> 'array'
                    OR jsonb_typeof(node -> 'acknowledgementPolicy') <> 'string'
                    OR jsonb_typeof(node -> 'retryPolicy') <> 'string' THEN
                RAISE EXCEPTION '旧物模型命令字段不合法: %', node_path;
            END IF;
            child_path := 'inputs';
        END IF;
        converted_array := '[]'::JSONB;
        seen_codes := '{}'::JSONB;
        FOR child IN SELECT value FROM jsonb_array_elements(node -> child_path) WITH ORDINALITY AS items(value, position) ORDER BY position
        LOOP
            IF jsonb_typeof(child -> 'code') <> 'string' OR btrim(child ->> 'code') = '' THEN
                RAISE EXCEPTION '旧物模型具名字段 code 不合法: %/%', node_path, child_path;
            END IF;
            child_code := child ->> 'code';
            IF seen_codes ? child_code THEN
                RAISE EXCEPTION '旧物模型具名字段 code 重复: %/%/%', node_path, child_path, child_code;
            END IF;
            seen_codes := seen_codes || jsonb_build_object(child_code, true);
            child_result := sca4dj_iot_convert_model_node(
                    child,
                    'FIELD',
                    node_path || '/' || child_path || '/' || sca4dj_iot_escape_pointer(child_code));
            converted_array := converted_array || jsonb_build_array(child_result -> 'model');
            converted_i18n := sca4dj_iot_merge_i18n(converted_i18n, child_result -> 'i18n', node_path);
        END LOOP;
        converted_model := jsonb_set(converted_model, ARRAY[child_path], converted_array);

    ELSIF node_kind = 'FIELD' THEN
        IF jsonb_typeof(node -> 'code') <> 'string'
                OR btrim(node ->> 'code') = ''
                OR jsonb_typeof(node -> 'valueType') <> 'object' THEN
            RAISE EXCEPTION '旧物模型具名字段不合法: %', node_path;
        END IF;
        code := node ->> 'code';
        converted_model := node;
        converted_i18n := jsonb_build_object(node_path || '/displayName', code);
        child_result := sca4dj_iot_convert_model_node(node -> 'valueType', 'TYPE', node_path || '/valueType');
        converted_model := jsonb_set(converted_model, '{valueType}', child_result -> 'model');
        converted_i18n := sca4dj_iot_merge_i18n(converted_i18n, child_result -> 'i18n', node_path);

    ELSIF node_kind = 'ENUM' THEN
        IF jsonb_typeof(node -> 'code') <> 'string' OR btrim(node ->> 'code') = '' THEN
            RAISE EXCEPTION '旧物模型枚举 code 不合法: %', node_path;
        END IF;
        code := node ->> 'code';
        label := CASE
            WHEN jsonb_typeof(node -> 'displayName') = 'string' AND btrim(node ->> 'displayName') <> ''
                THEN node ->> 'displayName'
            WHEN node ? 'displayName' THEN NULL
            ELSE code
        END;
        IF label IS NULL THEN
            RAISE EXCEPTION '旧物模型枚举 displayName 不合法: %', node_path;
        END IF;
        converted_model := node - 'displayName';
        converted_i18n := jsonb_build_object(node_path || '/displayName', label);

    ELSE
        IF jsonb_typeof(node -> 'kind') <> 'string' OR btrim(node ->> 'kind') = '' THEN
            RAISE EXCEPTION '旧物模型值类型 kind 不合法: %', node_path;
        END IF;
        converted_model := node;
        IF node ? 'enumValues' THEN
            IF jsonb_typeof(node -> 'enumValues') <> 'array' THEN
                RAISE EXCEPTION '旧物模型 enumValues 必须是 array: %', node_path;
            END IF;
            converted_array := '[]'::JSONB;
            seen_codes := '{}'::JSONB;
            FOR child IN SELECT value FROM jsonb_array_elements(node -> 'enumValues') WITH ORDINALITY AS items(value, position) ORDER BY position
            LOOP
                IF jsonb_typeof(child -> 'code') <> 'string' OR btrim(child ->> 'code') = '' THEN
                    RAISE EXCEPTION '旧物模型枚举 code 不合法: %', node_path;
                END IF;
                child_code := child ->> 'code';
                IF seen_codes ? child_code THEN
                    RAISE EXCEPTION '旧物模型枚举 code 重复: %/%', node_path, child_code;
                END IF;
                seen_codes := seen_codes || jsonb_build_object(child_code, true);
                child_result := sca4dj_iot_convert_model_node(
                        child,
                        'ENUM',
                        node_path || '/enumValues/' || sca4dj_iot_escape_pointer(child_code));
                converted_array := converted_array || jsonb_build_array(child_result -> 'model');
                converted_i18n := sca4dj_iot_merge_i18n(converted_i18n, child_result -> 'i18n', node_path);
            END LOOP;
            converted_model := jsonb_set(converted_model, '{enumValues}', converted_array);
        END IF;
        FOREACH child_path IN ARRAY ARRAY['requiredFields', 'optionalFields']
        LOOP
            IF node ? child_path THEN
                IF jsonb_typeof(node -> child_path) <> 'array' THEN
                    RAISE EXCEPTION '旧物模型 object 字段必须是 array: %/%', node_path, child_path;
                END IF;
                converted_array := '[]'::JSONB;
                seen_codes := '{}'::JSONB;
                FOR child IN SELECT value FROM jsonb_array_elements(node -> child_path) WITH ORDINALITY AS items(value, position) ORDER BY position
                LOOP
                    IF jsonb_typeof(child -> 'code') <> 'string' OR btrim(child ->> 'code') = '' THEN
                        RAISE EXCEPTION '旧物模型 object 字段 code 不合法: %/%', node_path, child_path;
                    END IF;
                    child_code := child ->> 'code';
                    IF seen_codes ? child_code THEN
                        RAISE EXCEPTION '旧物模型 object 字段 code 重复: %/%/%', node_path, child_path, child_code;
                    END IF;
                    seen_codes := seen_codes || jsonb_build_object(child_code, true);
                    child_result := sca4dj_iot_convert_model_node(
                            child,
                            'FIELD',
                            node_path || '/' || child_path || '/' || sca4dj_iot_escape_pointer(child_code));
                    converted_array := converted_array || jsonb_build_array(child_result -> 'model');
                    converted_i18n := sca4dj_iot_merge_i18n(converted_i18n, child_result -> 'i18n', node_path);
                END LOOP;
                converted_model := jsonb_set(converted_model, ARRAY[child_path], converted_array);
            END IF;
        END LOOP;
        IF node ? 'elementType' THEN
            IF jsonb_typeof(node -> 'elementType') <> 'object' THEN
                RAISE EXCEPTION '旧物模型 elementType 必须是 object: %', node_path;
            END IF;
            child_result := sca4dj_iot_convert_model_node(node -> 'elementType', 'TYPE', node_path || '/elementType');
            converted_model := jsonb_set(converted_model, '{elementType}', child_result -> 'model');
            converted_i18n := sca4dj_iot_merge_i18n(converted_i18n, child_result -> 'i18n', node_path);
        END IF;
    END IF;

    RETURN jsonb_build_object('model', converted_model, 'i18n', converted_i18n);
END
$$;

-- 按 ThingModelJsonCodec 的字段顺序生成无空白规范 JSON，供摘要重算。
CREATE FUNCTION sca4dj_iot_canonical_model_node(node JSONB, node_kind TEXT)
RETURNS TEXT
LANGUAGE plpgsql
IMMUTABLE
STRICT
AS $$
DECLARE
    result TEXT;
    array_text TEXT;
    child JSONB;
    first_item BOOLEAN;
BEGIN
    IF node_kind = 'MODEL' THEN
        result := '{"formatVersion":' || (node -> 'formatVersion')::TEXT
                || ',"productCode":' || (node -> 'productCode')::TEXT
                || ',"version":' || (node -> 'version')::TEXT;
        array_text := '[';
        first_item := TRUE;
        FOR child IN SELECT value FROM jsonb_array_elements(node -> 'properties') WITH ORDINALITY AS items(value, position) ORDER BY position
        LOOP
            array_text := array_text || CASE WHEN first_item THEN '' ELSE ',' END
                    || sca4dj_iot_canonical_model_node(child, 'PROPERTY');
            first_item := FALSE;
        END LOOP;
        result := result || ',"properties":' || array_text || ']';
        array_text := '[';
        first_item := TRUE;
        FOR child IN SELECT value FROM jsonb_array_elements(node -> 'events') WITH ORDINALITY AS items(value, position) ORDER BY position
        LOOP
            array_text := array_text || CASE WHEN first_item THEN '' ELSE ',' END
                    || sca4dj_iot_canonical_model_node(child, 'EVENT');
            first_item := FALSE;
        END LOOP;
        result := result || ',"events":' || array_text || ']';
        array_text := '[';
        first_item := TRUE;
        FOR child IN SELECT value FROM jsonb_array_elements(node -> 'commands') WITH ORDINALITY AS items(value, position) ORDER BY position
        LOOP
            array_text := array_text || CASE WHEN first_item THEN '' ELSE ',' END
                    || sca4dj_iot_canonical_model_node(child, 'COMMAND');
            first_item := FALSE;
        END LOOP;
        RETURN result || ',"commands":' || array_text || ']}';
    ELSIF node_kind = 'PROPERTY' THEN
        result := '{"code":' || (node -> 'code')::TEXT
                || ',"valueType":' || sca4dj_iot_canonical_model_node(node -> 'valueType', 'TYPE')
                || ',"access":' || (node -> 'access')::TEXT
                || ',"telemetry":' || (node -> 'telemetry')::TEXT;
        IF node ? 'acknowledgementPolicy' THEN
            result := result || ',"acknowledgementPolicy":' || (node -> 'acknowledgementPolicy')::TEXT;
        END IF;
        IF node ? 'retryPolicy' THEN
            result := result || ',"retryPolicy":' || (node -> 'retryPolicy')::TEXT;
        END IF;
        RETURN result || '}';
    ELSIF node_kind = 'EVENT' OR node_kind = 'COMMAND' THEN
        result := '{"code":' || (node -> 'code')::TEXT;
        IF node_kind = 'EVENT' THEN
            result := result || ',"delivery":' || (node -> 'delivery')::TEXT;
            array_text := '[';
            first_item := TRUE;
            FOR child IN SELECT value FROM jsonb_array_elements(node -> 'outputs') WITH ORDINALITY AS items(value, position) ORDER BY position
            LOOP
                array_text := array_text || CASE WHEN first_item THEN '' ELSE ',' END
                        || sca4dj_iot_canonical_model_node(child, 'FIELD');
                first_item := FALSE;
            END LOOP;
            RETURN result || ',"outputs":' || array_text || ']}';
        END IF;
        array_text := '[';
        first_item := TRUE;
        FOR child IN SELECT value FROM jsonb_array_elements(node -> 'inputs') WITH ORDINALITY AS items(value, position) ORDER BY position
        LOOP
            array_text := array_text || CASE WHEN first_item THEN '' ELSE ',' END
                    || sca4dj_iot_canonical_model_node(child, 'FIELD');
            first_item := FALSE;
        END LOOP;
        RETURN result || ',"inputs":' || array_text || ']'
                || ',"acknowledgementPolicy":' || (node -> 'acknowledgementPolicy')::TEXT
                || ',"retryPolicy":' || (node -> 'retryPolicy')::TEXT || '}';
    ELSIF node_kind = 'FIELD' THEN
        RETURN '{"code":' || (node -> 'code')::TEXT
                || ',"valueType":' || sca4dj_iot_canonical_model_node(node -> 'valueType', 'TYPE') || '}';
    ELSIF node_kind = 'ENUM' THEN
        RETURN '{"code":' || (node -> 'code')::TEXT || '}';
    ELSIF node_kind <> 'TYPE' THEN
        RAISE EXCEPTION '未知规范物模型节点类型: %', node_kind;
    END IF;

    result := '{"kind":' || (node -> 'kind')::TEXT;
    IF node ? 'minimum' THEN result := result || ',"minimum":' || (node -> 'minimum')::TEXT; END IF;
    IF node ? 'maximum' THEN result := result || ',"maximum":' || (node -> 'maximum')::TEXT; END IF;
    IF node ? 'scale' THEN result := result || ',"scale":' || (node -> 'scale')::TEXT; END IF;
    IF node ? 'unit' THEN result := result || ',"unit":' || (node -> 'unit')::TEXT; END IF;
    IF node ? 'maximumLength' THEN result := result || ',"maximumLength":' || (node -> 'maximumLength')::TEXT; END IF;
    IF node ? 'maximumItems' THEN result := result || ',"maximumItems":' || (node -> 'maximumItems')::TEXT; END IF;
    FOREACH node_kind IN ARRAY ARRAY['enumValues', 'requiredFields', 'optionalFields']
    LOOP
        IF node ? node_kind THEN
            array_text := '[';
            first_item := TRUE;
            FOR child IN SELECT value FROM jsonb_array_elements(node -> node_kind) WITH ORDINALITY AS items(value, position) ORDER BY position
            LOOP
                array_text := array_text || CASE WHEN first_item THEN '' ELSE ',' END
                        || sca4dj_iot_canonical_model_node(child, CASE WHEN node_kind = 'enumValues' THEN 'ENUM' ELSE 'FIELD' END);
                first_item := FALSE;
            END LOOP;
            result := result || ',"' || node_kind || '":' || array_text || ']';
        END IF;
    END LOOP;
    IF node ? 'elementType' THEN
        result := result || ',"elementType":' || sca4dj_iot_canonical_model_node(node -> 'elementType', 'TYPE');
    END IF;
    RETURN result || '}';
END
$$;

-- 将旧失败摘要映射为稳定 code；无法识别的自由文本不再继续保存。
CREATE FUNCTION sca4dj_iot_stable_failure_code(value TEXT)
RETURNS TEXT
LANGUAGE plpgsql
IMMUTABLE
AS $$
DECLARE
    normalized TEXT := btrim(value);
BEGIN
    IF value IS NULL THEN RETURN NULL; END IF;
    RETURN CASE normalized
        WHEN '命令重试次数已耗尽' THEN 'COMMAND_RETRY_EXHAUSTED'
        WHEN '状态同步策略只适用于 desired' THEN 'RETRY_POLICY_MISMATCH'
        WHEN '设备报告执行失败' THEN 'DEVICE_REPORTED_FAILURE'
        WHEN '策略不允许重试' THEN 'RETRY_DISABLED'
        WHEN 'ACK_REJECTED' THEN 'DEVICE_REPORTED_FAILURE'
        ELSE CASE
            WHEN normalized ~ '^[0-9]{4}$' OR normalized ~ '^[A-Z][A-Z0-9_]{0,127}$' THEN normalized
            ELSE 'LEGACY_FAILURE'
        END
    END;
END
$$;

ALTER TABLE iot_product ADD COLUMN i18n JSONB;
UPDATE iot_product
SET i18n = jsonb_build_object('zh-CN', jsonb_build_object('displayName', display_name));
ALTER TABLE iot_product ALTER COLUMN i18n SET NOT NULL;
ALTER TABLE iot_product DROP COLUMN display_name;
COMMENT ON COLUMN iot_product.i18n IS '按 Locale 和字段键保存的完整 Product 多语言文本';

ALTER TABLE iot_thing_model ADD COLUMN i18n JSONB;
DO $$
DECLARE
    model_row RECORD;
    converted JSONB;
    canonical_json TEXT;
BEGIN
    FOR model_row IN
        SELECT model.product_id, model.version, model.model_json, product.code AS product_code
        FROM iot_thing_model model
        INNER JOIN iot_product product ON product.id = model.product_id
        ORDER BY model.product_id, model.version
    LOOP
        IF model_row.model_json ->> 'productCode' IS DISTINCT FROM model_row.product_code
                OR model_row.model_json ->> 'version' IS DISTINCT FROM model_row.version::TEXT THEN
            RAISE EXCEPTION '旧物模型 Product 或版本与数据库主键不一致: productId=% version=%',
                    model_row.product_id, model_row.version;
        END IF;
        converted := sca4dj_iot_convert_model_node(model_row.model_json, 'MODEL', '/');
        canonical_json := sca4dj_iot_canonical_model_node(converted -> 'model', 'MODEL');
        UPDATE iot_thing_model
        SET model_json = converted -> 'model',
            i18n = jsonb_build_object('zh-CN', converted -> 'i18n'),
            content_hash = encode(sha256(convert_to(canonical_json, 'UTF8')), 'hex')
        WHERE product_id = model_row.product_id AND version = model_row.version;
    END LOOP;
END
$$;
ALTER TABLE iot_thing_model ALTER COLUMN i18n SET NOT NULL;
COMMENT ON COLUMN iot_thing_model.i18n IS '按 Locale 和稳定 JSON Pointer 路径保存的完整物模型多语言文本';
COMMENT ON COLUMN iot_thing_model.model_json IS 'formatVersion 2 的纯技术物模型 JSON，不包含展示文本';
COMMENT ON COLUMN iot_thing_model.content_hash IS '纯技术物模型规范 JSON 的 SHA-256';

ALTER TABLE iot_downlink_message RENAME COLUMN last_error TO last_failure_code;
UPDATE iot_downlink_message
SET last_failure_code = sca4dj_iot_stable_failure_code(last_failure_code)
WHERE last_failure_code IS NOT NULL;
ALTER TABLE iot_downlink_message
    ALTER COLUMN last_failure_code TYPE VARCHAR(128);
COMMENT ON COLUMN iot_downlink_message.last_failure_code IS '最近一次发送失败的稳定 code';

ALTER TABLE iot_device_schedule RENAME COLUMN last_failure TO last_failure_code;
UPDATE iot_device_schedule
SET last_failure_code = sca4dj_iot_stable_failure_code(last_failure_code)
WHERE last_failure_code IS NOT NULL;
ALTER TABLE iot_device_schedule
    ALTER COLUMN last_failure_code TYPE VARCHAR(128);
COMMENT ON COLUMN iot_device_schedule.last_failure_code IS '最近一次业务失败的稳定 code';

DROP FUNCTION sca4dj_iot_stable_failure_code(TEXT);
DROP FUNCTION sca4dj_iot_canonical_model_node(JSONB, TEXT);
DROP FUNCTION sca4dj_iot_convert_model_node(JSONB, TEXT, TEXT);
DROP FUNCTION sca4dj_iot_merge_i18n(JSONB, JSONB, TEXT);
DROP FUNCTION sca4dj_iot_escape_pointer(TEXT);
