package com.sjy.dd;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class CurrLocationInfoModel extends ViewModel {
   private MutableLiveData<String> address;
   private MutableLiveData<Double> longitude;
   private MutableLiveData<Double> latitude;

   public MutableLiveData<String> getCurrAddress() {
      if (address == null) {
         address = new MutableLiveData<String>();
      }
      return address;
   }

   public MutableLiveData<Double> getCurrLongitude() {
      if (longitude == null) {
         longitude = new MutableLiveData<Double>();
      }
      return longitude;
   }

   public MutableLiveData<Double> getCurrLatitude() {
      if (latitude == null) {
         latitude = new MutableLiveData<Double>();
      }
      return latitude;
   }
}
