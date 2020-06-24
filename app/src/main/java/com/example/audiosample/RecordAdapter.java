package com.example.audiosample;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.List;

public class RecordAdapter extends BaseAdapter {
    private Context context;
    private List<String> list;

    public RecordAdapter(Context context, List<String> list) {
        this.context = context;
        this.list = list;
    }

    @Override
    public int getCount() {
        return list.size();
    }

    @Override
    public Object getItem(int position) {
        return list;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder = new ViewHolder();
        if (convertView == null) {
            convertView = LayoutInflater.from(this.context).
                    inflate(R.layout.record_item,null,false);
            holder.name = (TextView) convertView.findViewById(R.id.item_name);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder)convertView.getTag();
        }
        holder.name.setText(list.get(position));
        return convertView;
    }

    public final class ViewHolder {
        public TextView name;
    }
}
