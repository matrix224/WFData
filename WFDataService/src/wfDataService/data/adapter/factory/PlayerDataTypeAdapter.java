package wfDataService.data.adapter.factory;

import java.io.IOException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.TypeAdapter;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import wfDataModel.model.data.PlayerData;
import wfDataModel.service.codes.JSONField;
import wfDataModel.service.type.PlatformType;

public class PlayerDataTypeAdapter extends TypeAdapter<PlayerData> {


	@Override
	public void write(JsonWriter out, PlayerData value) throws IOException {
		Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();
		JsonObject playerObj = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create().toJsonTree(value).getAsJsonObject();
		playerObj.addProperty(JSONField.PLATFORM, PlatformType.codeToType(value.getPlatform()).name());
		gson.toJson(playerObj, out);
	}

	@Override
	public PlayerData read(JsonReader in) throws IOException {
		JsonObject element = Streams.parse(in).getAsJsonObject();
		PlatformType type = PlatformType.valueOf(element.get(JSONField.PLATFORM).getAsString());
		int tCode = type.getCode();
		element.addProperty(JSONField.PLATFORM, tCode);

		return new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create().fromJson(element, PlayerData.class);
	}

}
