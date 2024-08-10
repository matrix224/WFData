package wfDataService.data.adapter;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;

import wfDataModel.model.data.PlayerData;
import wfDataService.data.adapter.factory.PlayerDataTypeAdapter;

public class PlayerDataTypeAdapterFactory implements TypeAdapterFactory {

	@SuppressWarnings("unchecked")
	@Override
	public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
		if (PlayerData.class.isAssignableFrom(type.getRawType())) {
			return (TypeAdapter<T>)new PlayerDataTypeAdapter();
		}
		return null;
	}

}
